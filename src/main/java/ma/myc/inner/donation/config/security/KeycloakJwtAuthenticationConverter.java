package ma.myc.inner.donation.config.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import ma.myc.inner.donation.util.constants.GlobalConstants;

/**
 * Token Keycloak -> droits Spring (modele C, ADR 01/10) :
 * <ul>
 * <li>{@code scope} -> {@code SCOPE_donation:read}, {@code SCOPE_donation:write} ;</li>
 * <li>{@code resource_access.donation-api.roles} -> permissions telles quelles ({@code donor:delete}, {@code donation:list}...) :
 * seules les permissions de l'API, celles des autres clients et les roles metier ({@code realm_access}) sont ignores ;</li>
 * <li>nom de l'utilisateur = {@code sub} (identifiant stable, sans donnee personnelle, base de l'ABAC).</li>
 * </ul>
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String RESOURCE_ACCESS = "resource_access";
	private static final String ROLES = "roles";

	private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<GrantedAuthority> authorities = Stream.concat(
						scopesConverter.convert(jwt).stream(),
						permissions(jwt).stream())
				.toList();
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}

	private static List<GrantedAuthority> permissions(Jwt jwt) {
		Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
		if (resourceAccess == null
				|| !(resourceAccess.get(GlobalConstants.RESOURCE_CLIENT_ID) instanceof Map<?, ?> client)
				|| !(client.get(ROLES) instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
				.map(String::valueOf)
				.map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
				.toList();
	}
}
