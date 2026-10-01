package ma.myc.inner.donation.config.security;

import ma.myc.inner.donation.api.DonationController;
import ma.myc.inner.donation.api.DonorController;
import ma.myc.inner.donation.config.ErrorHandlingAdvice;
import ma.myc.inner.donation.config.TestConfig;
import ma.myc.inner.donation.config.properties.MycSecurityProps;
import ma.myc.inner.donation.domain.dto.CreateDonorRequest;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.service.DonationService;
import ma.myc.inner.donation.service.DonorService;
import ma.myc.inner.donation.util.constants.GlobalConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaine de securite reelle (myc.security.enabled=true) : GET exige donation:read,
 * POST / PATCH / DELETE exigent donation:write (l'audience est verifiee par le decodeur, cf. JwtValidationTest).
 */
@WebMvcTest(controllers = {DonorController.class, DonationController.class})
@Import({SecurityConfig.class, SecurityAuthEntryPoint.class, SecurityAccessDeniedHandler.class,
        KeycloakJwtAuthenticationConverter.class, MycSecurityProps.class, ErrorHandlingAdvice.class, TestConfig.class})
@TestPropertySource(properties = "myc.security.enabled=true")
class SecurityConfigTest {

    private static final UUID DONOR_ID = UUID.randomUUID();
    private static final String DONOR_JSON = """
            {"lastName":"Molin","firstName":"Jean","email":"jean.molin@example.com","dateOfBirth":"1999-09-19","country":"MA"}""";

    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority(GlobalConstants.SCOPE_READ);
    private static final SimpleGrantedAuthority WRITE = new SimpleGrantedAuthority(GlobalConstants.SCOPE_WRITE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DonorService donorService;

    @MockitoBean
    private DonationService donationService;

    // Le jeton est fourni par jwt() : le decodeur n'est jamais appele, mais le resource server en exige un
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static DonorResponse donor() {
        return new DonorResponse(DONOR_ID, "Molin", "Jean", "jean.molin@example.com", LocalDate.of(1999, 9, 19), "MA");
    }

    @Test
    @DisplayName("GET /api/v1/donors/{id} without token returns 401 with a Bearer challenge")
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("GET with donation:read returns 200")
    void get_withReadScope_returns200() throws Exception {
        when(donorService.get(DONOR_ID)).thenReturn(donor());

        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(READ)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/donations with donation:read returns 200")
    void listDonations_withReadScope_returns200() throws Exception {
        when(donationService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/donations").accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(READ)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET with donation:write only returns 403 (write does not imply read)")
    void get_withWriteScopeOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(WRITE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("GET with the former inner:donation scope returns 403")
    void get_withFormerScope_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donations").accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_inner:donation"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donationService);
    }

    @Test
    @DisplayName("GET with a role but no scope returns 403 (audience alone is not enough)")
    void get_withRoleButNoScope_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("POST with donation:read only returns 403")
    void post_withReadScope_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/donors").contentType(MediaType.APPLICATION_JSON).content(DONOR_JSON)
                        .with(jwt().authorities(READ)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("POST with donation:write returns 201")
    void post_withWriteScope_returns201() throws Exception {
        when(donorService.create(any(CreateDonorRequest.class))).thenReturn(donor());

        mockMvc.perform(post("/api/v1/donors").contentType(MediaType.APPLICATION_JSON).content(DONOR_JSON)
                        .with(jwt().authorities(WRITE)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE with donation:read only returns 403")
    void delete_withReadScope_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/donors/{id}", DONOR_ID).with(jwt().authorities(READ)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("DELETE with donation:write returns 204")
    void delete_withWriteScope_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/donors/{id}", DONOR_ID).with(jwt().authorities(WRITE)))
                .andExpect(status().isNoContent());
    }
}
