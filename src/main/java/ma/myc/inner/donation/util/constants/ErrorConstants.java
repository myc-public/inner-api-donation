package ma.myc.inner.donation.util.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorConstants {
	/**
	 * API ERROR CODES
	 **/
	private static final String BASE_URI = "/problem";
	public static final String URI_DEFAULT = BASE_URI + "/error-with-detail";
	public static final String URI_SECURITY_FORBIDDEN = BASE_URI + "/security/forbidden";
	public static final String URI_SECURITY_AUTHORIZED = BASE_URI + "/security/authorized";
	public static final String URI_MISSING_REQUEST_PARAMETER = BASE_URI + "/missing-request-params";
	public static final String URI_HTTP_MESSAGE_NOT_READABLE = BASE_URI + "/http-message-not-readable";
	public static final String URI_METHOD_NOT_ALLOWED = BASE_URI + "/method-not-allowed";
	public static final String URI_METHOD_ARGUMENT_NOT_VALID = BASE_URI + "/method-argument-not-valid";
	public static final String URI_VALIDATION_CONSTRAINT_VIOLATION = BASE_URI + "/validation-constraint-violation";
	public static final String URI_INTER_SERVICE_COMMUNICATION = BASE_URI + "/inter-service-communication";
	public static final String URI_UNEXPECTED_TECHNICAL_ERROR = BASE_URI + "/unexpected-technical-exception";
	public static final String URI_BUSINESS_EXCEPTION = BASE_URI + "/business-exception";
	public static final String URI_NOT_FOUND = BASE_URI + "/not-found";
	public static final String URI_CONFLICT = BASE_URI + "/conflict";
	public static final String URI_ARGUMENT_TYPE_MISMATCH = BASE_URI + "/argument-type-mismatch";
	public static final String URI_UNSUPPORTED_MEDIA_TYPE = BASE_URI + "/unsupported-media-type";
	public static final String URI_NOT_ACCEPTABLE = BASE_URI + "/not-acceptable";

	/**
	 * MESSAGES
	 **/
	public static final String ERR_TECHNICAL = "error.technical.message";
	public static final String ERR_INTERNAL_SERVER = "error.internal.message";

}
