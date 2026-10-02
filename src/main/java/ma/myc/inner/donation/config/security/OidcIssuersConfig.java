package ma.myc.inner.donation.config.security;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import ma.myc.inner.donation.config.properties.OidcProps;

/**
 * Validation des tokens de plusieurs emetteurs (ADR 02/10, DI4) : un decodeur par emetteur declare
 * (signature par ses cles JWKS, iss, exp / nbf, aud). Un token d'un emetteur non declare est refuse (401).
 */
@Configuration
public class OidcIssuersConfig {

	@Bean
	JwtIssuerAuthenticationManagerResolver jwtIssuerAuthenticationManagerResolver(
			OidcProps oidcProps, ClaimsJwtAuthenticationConverter converter) {
		Map<String, AuthenticationManager> managers = oidcProps.getIssuers().stream()
				.collect(Collectors.toUnmodifiableMap(OidcProps.Issuer::getIssuerUri,
						issuer -> authenticationManager(jwtDecoder(issuer, oidcProps.getAudience()), converter)));
		return new JwtIssuerAuthenticationManagerResolver(managers::get);
	}

	static JwtDecoder jwtDecoder(OidcProps.Issuer issuer, String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer.getJwkSetUri()).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer.getIssuerUri()),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));
		return decoder;
	}

	private static AuthenticationManager authenticationManager(JwtDecoder decoder, ClaimsJwtAuthenticationConverter converter) {
		JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
		provider.setJwtAuthenticationConverter(converter);
		return provider::authenticate;
	}
}
