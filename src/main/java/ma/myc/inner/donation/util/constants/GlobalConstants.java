package ma.myc.inner.donation.util.constants;

public final class GlobalConstants {

	private GlobalConstants() throws InstantiationException {
		throw new InstantiationException("Instances of this type are forbidden");
	}

	/**
	 * SECURITE (Keycloak, realm myc)
	 **/
	// Client Keycloak de l'API : audience attendue et porteur des roles (resource_access.donation-api.roles)
	public static final String RESOURCE_CLIENT_ID = "donation-api";
	// Scopes OAuth2 demandes par les clients (le token doit porter l'audience ET le scope)
	public static final String SCOPE_DONATION_READ = "donation:read";
	public static final String SCOPE_DONATION_WRITE = "donation:write";
	public static final String SCOPE_READ = "SCOPE_" + SCOPE_DONATION_READ;
	public static final String SCOPE_WRITE = "SCOPE_" + SCOPE_DONATION_WRITE;

	/**
	 * API PATHS
	 **/
	public static final String API_V1_PATTERN = "/api/v1/**";

	/**
	 * OpenApi
	 **/
	public static final String INFO_API_TITLE = "MYC Donation API";
	public static final String INFO_API_DESCRIPTION = "Inner Donation Api description";
	public static final String INFO_API_TERMS_OF_SERVICE = "https://www.myc.ma/mentions-legales";
	public static final String CONTACT_NAME = "myc Morocco";
	public static final String CONTACT_EMAIL = "helpdesk@myc.ma";
	public static final String CONTACT_WEBSITE = "https://myc.ma";

	/**
	 * REQUEST HEADERS
	 **/
	public static final String HEADER_BEARER = "Bearer ";

}
