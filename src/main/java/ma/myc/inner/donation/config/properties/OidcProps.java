package ma.myc.inner.donation.config.properties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Emetteurs de tokens acceptes par l'API (ADR 02/10, DI4) : liste explicite, une entree par realm
 * (myc-internal, myc-customers). Changer d'IdP = changer cette configuration, jamais le code.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "myc.security.oidc")
public class OidcProps {

	/** Audience exigee dans chaque token (claim aud). */
	private String audience;

	/** Emetteurs de confiance : URL publique (iss) et URL interne des cles (JWKS, backchannel). */
	private List<Issuer> issuers = new ArrayList<>();

	@Data
	public static class Issuer {
		private String issuerUri;
		private String jwkSetUri;
	}
}
