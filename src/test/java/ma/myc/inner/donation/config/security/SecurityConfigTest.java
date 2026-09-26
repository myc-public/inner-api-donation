package ma.myc.inner.donation.config.security;

import ma.myc.inner.donation.api.DonationController;
import ma.myc.inner.donation.api.DonorController;
import ma.myc.inner.donation.config.ErrorHandlingAdvice;
import ma.myc.inner.donation.config.TestConfig;
import ma.myc.inner.donation.config.properties.MycSecurityProps;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaine de securite reelle (myc.security.enabled=true) : le scope inner:donation est exige sur /api/v1/**.
 */
@WebMvcTest(controllers = {DonorController.class, DonationController.class})
@Import({SecurityConfig.class, SecurityAuthEntryPoint.class, SecurityAccessDeniedHandler.class,
        MycSecurityProps.class, ErrorHandlingAdvice.class, TestConfig.class})
@TestPropertySource(properties = "myc.security.enabled=true")
class SecurityConfigTest {

    private static final UUID DONOR_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DonorService donorService;

    @MockitoBean
    private DonationService donationService;

    // Le jeton est fourni par jwt() : le decodeur n'est jamais appele, mais le resource server en exige un
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/donors/{id} without token returns 401")
    void donors_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("GET /api/v1/donors/{id} with a token lacking the inner:donation scope returns 403")
    void donors_tokenWithoutScope_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other:api"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("GET /api/v1/donations with a token lacking the inner:donation scope returns 403")
    void donations_tokenWithoutScope_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/donations").accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other:api"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donationService);
    }

    @Test
    @DisplayName("GET /api/v1/donors/{id} with the inner:donation scope returns 200")
    void donors_tokenWithScope_returns200() throws Exception {
        when(donorService.get(DONOR_ID)).thenReturn(
                new DonorResponse(DONOR_ID, "Molin", "Jean", "jean.molin@example.com", LocalDate.of(1999, 9, 19), "MA"));

        mockMvc.perform(get("/api/v1/donors/{id}", DONOR_ID).accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority(GlobalConstants.SCOPE))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/donations with the inner:donation scope returns 200")
    void donations_tokenWithScope_returns200() throws Exception {
        when(donationService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/donations").accept(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority(GlobalConstants.SCOPE))))
                .andExpect(status().isOk());
    }
}
