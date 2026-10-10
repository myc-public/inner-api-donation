package ma.myc.inner.donation.config.security;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.myc.inner.donation.domain.core.error.ApiError;
import ma.myc.inner.donation.util.SecurityUtils;
import ma.myc.inner.donation.util.component.TraceRequestHandler;
import ma.myc.inner.donation.util.constants.ErrorConstants;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

	private final TraceRequestHandler traceRequestHandler;
	private final JsonMapper jsonMapper;
	private final UrlPathHelper urlPathHelper = new UrlPathHelper();

	@Override
	public void handle(HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException)
			throws IOException, ServletException {
		String message;
		HttpStatus status = HttpStatus.FORBIDDEN;
		Map<String, String> parameters = new LinkedHashMap<>();

		// Start RFC
		if (request != null && request.getUserPrincipal() instanceof AbstractOAuth2TokenAuthenticationToken) {
			parameters.put("error", BearerTokenErrorCodes.INSUFFICIENT_SCOPE);
			parameters.put("error_description",
					"The request requires higher privileges than provided by the access token.");
			parameters.put("error_uri", "https://tools.ietf.org/html/rfc6750#section-3.1");
		}
		String wwwAuthenticate = SecurityUtils.computeWWWAuthenticateHeaderValue(parameters);
		response.addHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
		response.setStatus(status.value());
		// end RFC

		if (accessDeniedException.getCause() != null) {
			message = accessDeniedException.getCause().getMessage();
		} else {
			message = accessDeniedException.getMessage();
		}
		
		log.warn((request != null) ? "{} : {} {}" : "{}", 
				message, 
				(request != null) ? request.getMethod() : "N/A", 
				(request != null) ? request.getRequestURI() : "N/A");
		
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();
		var apiError = ApiError.builder()
				.type(ErrorConstants.URI_SECURITY_FORBIDDEN)
				.title(status.getReasonPhrase())
				.status(status.value())
				.detail(message != null ? message : "Access to this resource is denied")
				.traceId(traceRequestHandler.getCorrelationId())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.build();
		var jsonRes = jsonMapper.writeValueAsString(apiError);
		out.print(jsonRes);
		out.flush();
	}

}
