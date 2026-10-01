package ma.myc.inner.donation.config.security;

/**
 * Permissions de l'API (modele C, ADR 01/10), verifiees par {@code @PreAuthorize} sur chaque operation.
 * <p>
 * L'API ne connait que ces permissions, jamais les roles metier : la matrice role -> permissions est geree
 * dans le realm Keycloak (roles de realm composites, gitops-platform/apps/keycloak/base/realm/10-realm.yaml).
 * Les noms doivent rester identiques aux roles du client donation-api dans ce realm.
 */
public final class Permissions {

	private Permissions() throws InstantiationException {
		throw new InstantiationException("Instances of this type are forbidden");
	}

	public static final String DONOR_CREATE = "hasAuthority('donor:create')";
	public static final String DONOR_READ = "hasAuthority('donor:read')";
	public static final String DONOR_LIST = "hasAuthority('donor:list')";
	public static final String DONOR_UPDATE = "hasAuthority('donor:update')";
	public static final String DONOR_DELETE = "hasAuthority('donor:delete')";

	public static final String DONATION_CREATE = "hasAuthority('donation:create')";
	public static final String DONATION_READ = "hasAuthority('donation:read')";
	public static final String DONATION_LIST = "hasAuthority('donation:list')";
	public static final String DONATION_UPDATE = "hasAuthority('donation:update')";
	public static final String DONATION_DELETE = "hasAuthority('donation:delete')";
}
