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

class ClaimsJwtAuthenticationConverterTest {

    private static final String PARTY_ID = "8f098e43-e3c0-4143-aac7-dc0a9a7bffbd";

    private final ClaimsJwtAuthenticationConverter converter = new ClaimsJwtAuthenticationConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("idp-internal-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("scopes become SCOPE_ authorities and the permissions claim becomes permissions as is")
    void convert_scopesAndPermissions() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read donation:write",
                "permissions", List.of("donor:read", "donor:delete"))));

        assertThat(authorities(token)).containsExactlyInAnyOrder(
                "SCOPE_donation:read", "SCOPE_donation:write", "donor:read", "donor:delete");
    }

    @Test
    @DisplayName("IdP-specific structures (resource_access, realm_access) are ignored")
    void convert_idpSpecificClaims_ignored() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read",
                "realm_access", Map.of("roles", List.of("donation-admin")),
                "resource_access", Map.of("donation-api", Map.of("roles", List.of("donor:delete"))))));

        assertThat(authorities(token)).containsExactly("SCOPE_donation:read");
    }

    @Test
    @DisplayName("malformed permissions claim (not a list) grants no permission")
    void convert_malformedPermissions_noPermission() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read", "permissions", "donor:delete")));

        assertThat(authorities(token)).containsExactly("SCOPE_donation:read");
    }

    @Test
    @DisplayName("principal name is the party_id claim, never the IdP sub")
    void convert_nameIsPartyId() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read", "party_id", PARTY_ID, "email", "donor.a@example.test")));

        assertThat(token.getName()).isEqualTo(PARTY_ID);
    }

    @Test
    @DisplayName("service account without party_id is named after its client (azp)")
    void convert_serviceAccount_nameIsAzp() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
                "scope", "donation:read", "azp", "donation-service")));

        assertThat(token.getName()).isEqualTo("donation-service");
    }
}
