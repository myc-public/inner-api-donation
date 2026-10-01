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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation reelle des tokens par l'auto-configuration Boot du resource server, avec les memes proprietes
 * que application.yml (issuer-uri, jwk-set-uri, audiences) : les cles sont servies par un JWKS de test.
 */
class JwtValidationTest {

    // Memes valeurs que spring.security.oauth2.resourceserver.jwt dans application.yml
    private static final String ISSUER = "http://localhost:8180/realms/myc";
    private static final String AUDIENCE = "donation-api";

    private static HttpServer jwksServer;
    private static RSAKey realmKey;
    private static RSAKey foreignKey;
    private static String jwkSetUri;

    @BeforeAll
    static void startJwksServer() throws JOSEException, IOException {
        realmKey = new RSAKeyGenerator(2048).keyID("realm-key").generate();
        foreignKey = new RSAKeyGenerator(2048).keyID("realm-key").generate();
        byte[] jwks = new JWKSet(realmKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/certs", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        jwksServer.start();
        jwkSetUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/certs";
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            // Securite web Boot (HttpSecurity) + resource server : la chaine par defaut du resource server en a besoin
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class))
            .withPropertyValues(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER,
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + jwkSetUri,
                    "spring.security.oauth2.resourceserver.jwt.audiences=" + AUDIENCE);

    private static String token(RSAKey key, String issuer, String audience, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .claim("scope", "donation:read")
                .issueTime(Date.from(expiresAt.minusSeconds(300)))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static Instant inFiveMinutes() {
        return Instant.now().plusSeconds(300);
    }

    @Test
    @DisplayName("valid token (realm key, issuer, audience) is accepted")
    void validToken_accepted() {
        runner.run(context -> {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            String token = token(realmKey, ISSUER, AUDIENCE, inFiveMinutes());

            assertThat(decoder.decode(token).getClaimAsString("scope")).isEqualTo("donation:read");
        });
    }

    @Test
    @DisplayName("token from another issuer (e.g. master realm) is rejected")
    void otherIssuer_rejected() {
        runner.run(context -> {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            String token = token(realmKey, "http://localhost:8180/realms/master", AUDIENCE, inFiveMinutes());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class).hasMessageContaining("iss");
        });
    }

    @Test
    @DisplayName("token issued for another audience is rejected")
    void otherAudience_rejected() {
        runner.run(context -> {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            String token = token(realmKey, ISSUER, "other-api", inFiveMinutes());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class).hasMessageContaining("aud");
        });
    }

    @Test
    @DisplayName("expired token is rejected")
    void expiredToken_rejected() {
        runner.run(context -> {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            String token = token(realmKey, ISSUER, AUDIENCE, Instant.now().minusSeconds(600));

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class).hasMessageContaining("expired");
        });
    }

    @Test
    @DisplayName("token signed with a key that is not in the realm JWKS is rejected")
    void foreignSignature_rejected() {
        runner.run(context -> {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            String token = token(foreignKey, ISSUER, AUDIENCE, inFiveMinutes());

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        });
    }
}
