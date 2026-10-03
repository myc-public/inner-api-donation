package ma.myc.inner.donation.config.security;

import ma.myc.inner.donation.api.MyDonationController;
import ma.myc.inner.donation.api.MyDonorController;
import ma.myc.inner.donation.config.ErrorHandlingAdvice;
import ma.myc.inner.donation.config.TestConfig;
import ma.myc.inner.donation.config.properties.MycSecurityProps;
import ma.myc.inner.donation.config.properties.OidcProps;
import ma.myc.inner.donation.domain.dto.CreateMyDonationRequest;
import ma.myc.inner.donation.domain.dto.RegisterDonorRequest;
import ma.myc.inner.donation.exception.DonorAlreadyExistsException;
import ma.myc.inner.donation.exception.DonorProfileRequiredException;
import ma.myc.inner.donation.exception.NotFoundException;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints /me du donateur (K4c, DC1-DC4) : l'identite vient TOUJOURS du token (party_id, email), jamais de la requete.
 */
@WebMvcTest(controllers = {MyDonorController.class, MyDonationController.class})
@Import({SecurityConfig.class, SecurityAuthEntryPoint.class, SecurityAccessDeniedHandler.class, CurrentParty.class,
        ClaimsJwtAuthenticationConverter.class, OidcIssuersConfig.class, OidcProps.class, MycSecurityProps.class,
        ErrorHandlingAdvice.class, TestConfig.class})
@TestPropertySource(properties = {"myc.security.enabled=true", "myc.security.oidc.audience=donation-api",
        "myc.security.oidc.issuers[0].issuer-uri=http://localhost:8180/realms/myc-customers",
        "myc.security.oidc.issuers[0].jwk-set-uri=http://localhost:8180/realms/myc-customers/protocol/openid-connect/certs"})
class MyEndpointsTest {

    private static final UUID PARTY_ID = UUID.fromString("8f098e43-e3c0-4143-aac7-dc0a9a7bffbd");
    private static final String EMAIL = "donor.a@example.test";
    private static final String OWN = "donor:create:own,donor:read:own,donation:create:own,donation:read:own,donation:list:own";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DonorService donorService;

    @MockitoBean
    private DonationService donationService;

    /** Token d'un donateur : party_id + email + permissions :own + deux scopes. */
    private static JwtRequestPostProcessor donor() {
        return jwt().jwt(j -> j.claim("party_id", PARTY_ID.toString()).claim("email", EMAIL))
                .authorities(authorities(OWN));
    }

    private static SimpleGrantedAuthority[] authorities(String permissions) {
        return java.util.stream.Stream.concat(java.util.Arrays.stream(permissions.split(",")),
                        java.util.stream.Stream.of(GlobalConstants.SCOPE_READ, GlobalConstants.SCOPE_WRITE))
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
    }

    @Test
    @DisplayName("POST /donors/me: profile created with party_id and email from the token, body cannot impose them")
    void registerSelf_identityFromToken() throws Exception {
        mockMvc.perform(post("/api/v1/donors/me").with(donor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"donorId":"00000000-0000-0000-0000-000000000001","email":"someone.else@example.test",
                                 "lastName":"A","firstName":"Donor","country":"MA"}"""))
                .andExpect(status().isCreated());

        verify(donorService).registerSelf(eq(PARTY_ID), eq(EMAIL), any(RegisterDonorRequest.class));
    }

    @Test
    @DisplayName("POST /donors/me: account not linked to a person (no party_id) -> 403, nothing created")
    void registerSelf_noPartyId_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/donors/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"A\",\"firstName\":\"Donor\"}")
                        .with(jwt().jwt(j -> j.claim("email", EMAIL)).authorities(authorities(OWN))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("POST /donors/me: second profile for the same person -> 409")
    void registerSelf_twice_conflict() throws Exception {
        when(donorService.registerSelf(eq(PARTY_ID), eq(EMAIL), any()))
                .thenThrow(new DonorAlreadyExistsException(DonorAlreadyExistsException.PROFILE_MESSAGE));

        mockMvc.perform(post("/api/v1/donors/me").with(donor()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"A\",\"firstName\":\"Donor\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /donors/me: reads the profile of the token's party_id")
    void getMe_usesPartyId() throws Exception {
        mockMvc.perform(get("/api/v1/donors/me").with(donor())).andExpect(status().isOk());

        verify(donorService).get(PARTY_ID);
    }

    @Test
    @DisplayName("GET /donors/me with a global permission only (internal agent) -> 403")
    void getMe_globalPermissionOnly_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/donors/me").with(jwt()
                        .jwt(j -> j.claim("party_id", PARTY_ID.toString())).authorities(authorities("donor:read,donor:list"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("GET /donations/me: lists the donations of the token's party_id")
    void listMine_usesPartyId() throws Exception {
        mockMvc.perform(get("/api/v1/donations/me").with(donor())).andExpect(status().isOk());

        verify(donationService).listByDonor(PARTY_ID);
    }

    @Test
    @DisplayName("GET /donations/me/{id}: donation of another donor -> 404 (DC3)")
    void getMine_notOwned_notFound() throws Exception {
        UUID donationId = UUID.randomUUID();
        when(donationService.getForDonor(donationId, PARTY_ID)).thenThrow(new NotFoundException("Donation not found"));

        mockMvc.perform(get("/api/v1/donations/me/{id}", donationId).with(donor())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /donations/me: created for the token's party_id ; without profile -> 409 (DC4)")
    void createMine_noProfile_conflict() throws Exception {
        when(donationService.createForDonor(eq(PARTY_ID), any(CreateMyDonationRequest.class)))
                .thenThrow(new DonorProfileRequiredException());

        mockMvc.perform(post("/api/v1/donations/me").with(donor()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FOOD\",\"type\":true,\"amount\":50}"))
                .andExpect(status().isConflict());

        verify(donationService).createForDonor(eq(PARTY_ID), any(CreateMyDonationRequest.class));
    }
}
