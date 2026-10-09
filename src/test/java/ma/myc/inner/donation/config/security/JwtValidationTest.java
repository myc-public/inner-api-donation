package ma.myc.inner.donation.config.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import ma.myc.inner.donation.config.properties.OidcProps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation reelle des tokens par le resolveur multi-emetteurs (ADR 02/10, DI4) : deux realms declares
 * (myc-internal, myc-customers), chacun avec ses propres cles servies par un JWKS de test.
 */
class JwtValidationTest {

    private static final String INTERNAL = "http://localhost:8180/realms/myc-internal";
    private static final String CUSTOMERS = "http://localhost:8180/realms/myc-customers";
    private static final String AUDIENCE = "donation-api";

    private static HttpServer jwksServer;
    private static RSAKey internalKey;
    private static RSAKey customersKey;
    private static JwtIssuerAuthenticationManagerResolver resolver;

    @BeforeAll
    static void startJwksServer() throws JOSEException, IOException {
        internalKey = new RSAKeyGenerator(2048).keyID("internal-key").generate();
        customersKey = new RSAKeyGenerator(2048).keyID("customers-key").generate();

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serve("/internal/certs", internalKey);
        serve("/customers/certs", customersKey);
        jwksServer.start();
        String base = "http://127.0.0.1:" + jwksServer.getAddress().getPort();

        OidcProps props = new OidcProps();
        props.setAudience(AUDIENCE);
        props.setIssuers(List.of(issuer(INTERNAL, base + "/internal/certs"), issuer(CUSTOMERS, base + "/customers/certs")));
        resolver = new OidcIssuersConfig().jwtIssuerAuthenticationManagerResolver(props, new ClaimsJwtAuthenticationConverter());
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    private static void serve(String path, RSAKey key) {
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
    }

    private static OidcProps.Issuer issuer(String issuerUri, String jwkSetUri) {
        OidcProps.Issuer issuer = new OidcProps.Issuer();
        issuer.setIssuerUri(issuerUri);
        issuer.setJwkSetUri(jwkSetUri);
        return issuer;
    }

    private static String token(RSAKey key, String issuer, String audience, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .claim("party_id", "8f098e43-e3c0-4143-aac7-dc0a9a7bffbd")
                .claim("scope", "donation:read")
                .claim("permissions", List.of("donor:read:own"))
                .issueTime(Date.from(expiresAt.minusSeconds(300)))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static Authentication authenticate(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return resolver.resolve(request).authenticate(new BearerTokenAuthenticationToken(token));
    }

    private static Instant inFiveMinutes() {
        return Instant.now().plusSeconds(300);
    }

    @Test
    @DisplayName("token of the internal realm is accepted")
    void internalToken_accepted() throws JOSEException {
        Authentication auth = authenticate(token(internalKey, INTERNAL, AUDIENCE, inFiveMinutes()));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo("8f098e43-e3c0-4143-aac7-dc0a9a7bffbd");
    }

    @Test
    @DisplayName("token of the customers realm is accepted, with its permissions")
    void customersToken_accepted() throws JOSEException {
        Authentication auth = authenticate(token(customersKey, CUSTOMERS, AUDIENCE, inFiveMinutes()));

        assertThat(auth.getAuthorities()).extracting("authority").contains("SCOPE_donation:read", "donor:read:own");
    }

    @Test
    @DisplayName("token from an undeclared issuer is rejected")
    void undeclaredIssuer_rejected() throws JOSEException {
        String token = token(internalKey, "http://localhost:8180/realms/master", AUDIENCE, inFiveMinutes());

        assertThatThrownBy(() -> authenticate(token)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("token of one realm signed with the other realm's key is rejected")
    void crossRealmSignature_rejected() throws JOSEException {
        String token = token(customersKey, INTERNAL, AUDIENCE, inFiveMinutes());

        assertThatThrownBy(() -> authenticate(token)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("token issued for another audience is rejected")
    void otherAudience_rejected() throws JOSEException {
        String token = token(customersKey, CUSTOMERS, "other-api", inFiveMinutes());

        assertThatThrownBy(() -> authenticate(token)).isInstanceOf(AuthenticationException.class).hasMessageContaining("aud");
    }

    @Test
    @DisplayName("expired token is rejected")
    void expiredToken_rejected() throws JOSEException {
        String token = token(internalKey, INTERNAL, AUDIENCE, Instant.now().minusSeconds(600));

        assertThatThrownBy(() -> authenticate(token)).isInstanceOf(AuthenticationException.class).hasMessageContaining("expired");
    }
}
