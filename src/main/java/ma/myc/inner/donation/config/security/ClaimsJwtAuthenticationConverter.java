package ma.myc.inner.donation.config.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Token -> droits Spring, selon le CONTRAT DE CLAIMS du SI (ADR 02/10, DI3), independant de l'IdP :
 * <ul>
 * <li>{@code scope} -> {@code SCOPE_donation:read}, {@code SCOPE_donation:write} ;</li>
 * <li>{@code permissions} (liste a plat, pour cette API) -> permissions telles quelles ({@code donor:delete}...) ;</li>
 * <li>nom = {@code party_id} (identifiant metier de la personne) ; a defaut (compte de service), {@code azp}.</li>
 * </ul>
 * Aucune structure propre a un IdP (resource_access, realm_access, sub) n'est lue.
 */
@Component
public class ClaimsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	public static final String CLAIM_PERMISSIONS = "permissions";
	public static final String CLAIM_PARTY_ID = "party_id";
	public static final String CLAIM_ACTOR_TYPE = "actor_type";
	private static final String CLAIM_AUTHORIZED_PARTY = "azp";

	private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<GrantedAuthority> authorities = Stream.concat(
						scopesConverter.convert(jwt).stream(),
						permissions(jwt).stream())
				.toList();
		return new JwtAuthenticationToken(jwt, authorities, principalName(jwt));
	}

	private static List<GrantedAuthority> permissions(Jwt jwt) {
		if (!(jwt.getClaims().get(CLAIM_PERMISSIONS) instanceof Collection<?> permissions)) {
			return List.of();
		}
		return permissions.stream()
				.map(String::valueOf)
				.map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
				.toList();
	}

	private static String principalName(Jwt jwt) {
		String partyId = jwt.getClaimAsString(CLAIM_PARTY_ID);
		return partyId != null ? partyId : jwt.getClaimAsString(CLAIM_AUTHORIZED_PARTY);
	}
}
