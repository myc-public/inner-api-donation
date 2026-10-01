package ma.myc.inner.donation.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private static final String SUB = "8f3c2a1e-4b5d-4c6e-9f70-1a2b3c4d5e6f";

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("scopes become SCOPE_ authorities and donation-api client roles become permissions as is")
    void convert_scopesAndApiPermissions() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read donation:write",
                "resource_access", Map.of("donation-api", Map.of("roles", List.of("donor:read", "donor:delete"))))));

        assertThat(authorities(token)).containsExactlyInAnyOrder(
                "SCOPE_donation:read", "SCOPE_donation:write", "donor:read", "donor:delete");
    }

    @Test
    @DisplayName("permissions of other clients and business (realm) roles are ignored")
    void convert_otherClientsAndRealmRoles_ignored() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read",
                "realm_access", Map.of("roles", List.of("donation-admin")),
                "resource_access", Map.of(
                        "donation-api", Map.of("roles", List.of("donor:read")),
                        "other-api", Map.of("roles", List.of("donor:delete"))))));

        assertThat(authorities(token)).containsExactlyInAnyOrder("SCOPE_donation:read", "donor:read");
    }

    @Test
    @DisplayName("token without resource_access only carries its scopes")
    void convert_noResourceAccess_scopesOnly() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("scope", "donation:read")));

        assertThat(authorities(token)).containsExactly("SCOPE_donation:read");
    }

    @Test
    @DisplayName("malformed resource_access (roles not a list) grants no permission")
    void convert_malformedRoles_noPermission() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read",
                "resource_access", Map.of("donation-api", Map.of("roles", "donor:delete")))));

        assertThat(authorities(token)).containsExactly("SCOPE_donation:read");
    }

    @Test
    @DisplayName("principal name is the sub claim (no personal data)")
    void convert_nameIsSub() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read", "preferred_username", "donor.a", "email", "donor.a@example.test")));

        assertThat(token.getName()).isEqualTo(SUB);
    }
}
