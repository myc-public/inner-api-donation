package ma.myc.inner.donation.config.security;

/**
 * Auteur d'une action, lu dans le token (contrat de claims, DI3 + DA5) : sert a l'audit (K4d).
 * Tous les champs sont optionnels (null si le claim est absent) : l'audit ne bloque jamais une action.
 *
 * @param partyId   identifiant stable de la personne ({@code party_id}), absent pour un compte de service
 * @param username  identite lisible ({@code preferred_username})
 * @param actorType {@code internal} / {@code customer} ({@code actor_type})
 * @param teamId    equipe de l'agent ou du superviseur ({@code team_id}), absent pour un admin
 * @param clientId  application appelante ({@code azp})
 * @param issuer    realm emetteur du token ({@code iss})
 */
public record Actor(String partyId, String username, String actorType, String teamId, String clientId, String issuer) {

	/** Aucun token (securite desactivee en local, traitement hors requete) : auteur inconnu, l'action continue. */
	public static final Actor UNKNOWN = new Actor(null, null, null, null, null, null);
}
