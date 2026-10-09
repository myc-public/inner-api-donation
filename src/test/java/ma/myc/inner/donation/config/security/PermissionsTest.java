package ma.myc.inner.donation.config.security;

import ma.myc.inner.donation.api.DonationController;
import ma.myc.inner.donation.api.DonorController;
import ma.myc.inner.donation.config.ErrorHandlingAdvice;
import ma.myc.inner.donation.config.TestConfig;
import ma.myc.inner.donation.config.properties.MycSecurityProps;
import ma.myc.inner.donation.config.properties.OidcProps;
import ma.myc.inner.donation.service.DonationService;
import ma.myc.inner.donation.service.DonorService;
import ma.myc.inner.donation.util.constants.GlobalConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Modele C (ADR 01/10) : chaque operation exige SA permission (@PreAuthorize).
 * Les deux scopes sont toujours fournis pour isoler le controle des permissions (K3) de celui des scopes (K2).
 * La matrice role -> permissions est dans le realm Keycloak et prouvee avec le Keycloak local.
 */
@WebMvcTest(controllers = {DonorController.class, DonationController.class})
@Import({SecurityConfig.class, SecurityAuthEntryPoint.class, SecurityAccessDeniedHandler.class,
        ClaimsJwtAuthenticationConverter.class, OidcIssuersConfig.class, OidcProps.class, MycSecurityProps.class, ErrorHandlingAdvice.class, TestConfig.class})
@TestPropertySource(properties = {"myc.security.enabled=true", "myc.security.oidc.audience=donation-api",
        "myc.security.oidc.issuers[0].issuer-uri=http://localhost:8180/realms/myc-internal",
        "myc.security.oidc.issuers[0].jwk-set-uri=http://localhost:8180/realms/myc-internal/protocol/openid-connect/certs"})
class PermissionsTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final List<String> ALL_PERMISSIONS = List.of(
            "donor:create", "donor:read", "donor:list", "donor:update", "donor:delete",
            "donation:create", "donation:read", "donation:list", "donation:update", "donation:delete");

    private static final String DONOR_JSON = """
            {"lastName":"Molin","firstName":"Jean","email":"jean.molin@example.com","dateOfBirth":"1999-09-19","country":"MA"}""";
    private static final String DONOR_PATCH_JSON = """
            {"lastName":"Molina"}""";
    private static final String DONATION_JSON = """
            {"category":"FOOD","type":true,"amount":50,"donor":{"donorId":"%s"}}""".formatted(ID);
    private static final String DONATION_PATCH_JSON = """
            {"amount":75}""";

    @Autowired
    private MockMvc mockMvc;

    // Services simules : sans stub, ils renvoient null / listes vides (seul le statut HTTP compte ici)
    @MockitoBean
    private DonorService donorService;

    @MockitoBean
    private DonationService donationService;


    static Stream<Arguments> operations() {
        return Stream.of(
                op("POST /donors", () -> post("/api/v1/donors").contentType(MediaType.APPLICATION_JSON).content(DONOR_JSON), "donor:create", 201),
                op("GET /donors/{id}", () -> get("/api/v1/donors/{id}", ID), "donor:read", 200),
                op("GET /donors", () -> get("/api/v1/donors"), "donor:list", 200),
                op("PATCH /donors/{id}", () -> patch("/api/v1/donors/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(DONOR_PATCH_JSON), "donor:update", 200),
                op("DELETE /donors/{id}", () -> delete("/api/v1/donors/{id}", ID), "donor:delete", 204),
                op("POST /donations", () -> post("/api/v1/donations").contentType(MediaType.APPLICATION_JSON).content(DONATION_JSON), "donation:create", 201),
                op("GET /donations/{id}", () -> get("/api/v1/donations/{id}", ID), "donation:read", 200),
                op("GET /donations", () -> get("/api/v1/donations"), "donation:list", 200),
                op("GET /donations/by-donor/{donorId}", () -> get("/api/v1/donations/by-donor/{id}", ID), "donation:read", 200),
                op("PATCH /donations/{id}", () -> patch("/api/v1/donations/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(DONATION_PATCH_JSON), "donation:update", 200),
                op("DELETE /donations/{id}", () -> delete("/api/v1/donations/{id}", ID), "donation:delete", 204));
    }

    private static Arguments op(String name, Supplier<MockHttpServletRequestBuilder> request, String permission, int okStatus) {
        return Arguments.of(name, request, permission, okStatus);
    }

    private static SimpleGrantedAuthority[] authorities(Stream<String> permissions) {
        return Stream.concat(permissions, Stream.of(GlobalConstants.SCOPE_READ, GlobalConstants.SCOPE_WRITE))
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
    }

    @ParameterizedTest(name = "{0} without {2} (all other permissions) -> 403")
    @MethodSource("operations")
    void operation_withoutItsPermission_returns403(String name, Supplier<MockHttpServletRequestBuilder> request,
                                                    String permission, int okStatus) throws Exception {
        mockMvc.perform(request.get().with(jwt().authorities(authorities(
                        ALL_PERMISSIONS.stream().filter(p -> !p.equals(permission))))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(donorService, donationService);
    }

    @ParameterizedTest(name = "{0} with {2} only -> {3}")
    @MethodSource("operations")
    void operation_withItsPermissionOnly_isAllowed(String name, Supplier<MockHttpServletRequestBuilder> request,
                                                   String permission, int okStatus) throws Exception {
        mockMvc.perform(request.get().with(jwt().authorities(authorities(Stream.of(permission)))))
                .andExpect(status().is(okStatus));
    }
}
