package ma.myc.inner.donation.config.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Identite de la personne connectee, lue dans le token valide (contrat de claims, ADR 02/10) :
 * seul endroit de l'API qui sait d'ou vient l'identite. Les endpoints /me ne prennent JAMAIS l'identite
 * dans l'URL ou le corps (pas d'acces a la ressource d'un autre, par construction).
 */
@Component
public class CurrentParty {

	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_USERNAME = "preferred_username";
	private static final String CLAIM_TEAM_ID = "team_id";
	private static final String CLAIM_AUTHORIZED_PARTY = "azp";

	/** party_id de la personne connectee ; 403 si le compte n'est pas encore rattache a une personne. */
	public UUID partyId() {
		String partyId = jwt().getToken().getClaimAsString(ClaimsJwtAuthenticationConverter.CLAIM_PARTY_ID);
		if (partyId == null) {
			throw new AccessDeniedException("Account not linked to a person (no party_id)");
		}
		try {
			return UUID.fromString(partyId);
		} catch (IllegalArgumentException e) {
			throw new AccessDeniedException("Invalid party_id in token");
		}
	}

	/** Email verifie par l'IdP (claim OIDC standard) ; 403 s'il est absent. */
	public String email() {
		String email = jwt().getToken().getClaimAsString(CLAIM_EMAIL);
		if (email == null || email.isBlank()) {
			throw new AccessDeniedException("No email in token");
		}
		return email;
	}

	/** Auteur de l'action courante pour l'audit (DA5) : ne leve jamais d'exception, champs null si absents. */
	public Actor actor() {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
			return Actor.UNKNOWN;
		}
		Jwt jwt = token.getToken();
		return new Actor(
				jwt.getClaimAsString(ClaimsJwtAuthenticationConverter.CLAIM_PARTY_ID),
				jwt.getClaimAsString(CLAIM_USERNAME),
				jwt.getClaimAsString(ClaimsJwtAuthenticationConverter.CLAIM_ACTOR_TYPE),
				firstValue(jwt.getClaims().get(CLAIM_TEAM_ID)),
				jwt.getClaimAsString(CLAIM_AUTHORIZED_PARTY),
				jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
	}

	/** team_id est mono-valeur dans le realm ; une liste (personne dans deux equipes) donne la premiere valeur. */
	private static String firstValue(Object claim) {
		if (claim instanceof Collection<?> values) {
			return values.isEmpty() ? null : String.valueOf(values.iterator().next());
		}
		return claim != null ? String.valueOf(claim) : null;
	}

	private static JwtAuthenticationToken jwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			return token;
		}
		throw new AccessDeniedException("No bearer token");
	}
}
