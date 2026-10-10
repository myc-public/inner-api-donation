package ma.myc.inner.donation.config.security;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
public class SecurityAuthEntryPoint implements AuthenticationEntryPoint {

	private final TraceRequestHandler traceRequestHandler;
	private final JsonMapper jsonMapper;
	private final UrlPathHelper urlPathHelper = new UrlPathHelper();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException)
			throws IOException, ServletException {

		String message;
		HttpStatus status = HttpStatus.UNAUTHORIZED;
		Map<String, String> parameters = new LinkedHashMap<>();

		// Oauth2 RFC6750 : https://www.rfc-editor.org/rfc/rfc6750#section-3
		if (authException instanceof OAuth2AuthenticationException authException2) {
			OAuth2Error error = authException2.getError();
			parameters.put("error", error.getErrorCode());
			if (StringUtils.hasText(error.getDescription())) {
				parameters.put("error_description", error.getDescription());
			}
			if (StringUtils.hasText(error.getUri())) {
				parameters.put("error_uri", error.getUri());
			}
			if (error instanceof BearerTokenError bearerTokenError) {
				if (StringUtils.hasText(bearerTokenError.getScope())) {
					parameters.put("scope", bearerTokenError.getScope());
				}
				status = ((BearerTokenError) error).getHttpStatus();
			}
		}
		String wwwAuthenticate = SecurityUtils.computeWWWAuthenticateHeaderValue(parameters);
		response.addHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
		response.setStatus(status.value());
		// end RFC6750

		if (authException.getCause() != null) {
			message = authException.getCause().getMessage();
		} else {
			message = authException.getMessage();
		}
		log.warn("{} : {} {}", message, request.getMethod(), request.getRequestURI());

		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();
		ApiError apiError = ApiError.builder()
				.type(ErrorConstants.URI_SECURITY_AUTHORIZED)
				.title(status.getReasonPhrase())
				.status(status.value())
				.traceId(traceRequestHandler.getCorrelationId())
				.detail(message != null ? message : "Access to this resource is denied")
				.instance(urlPathHelper.getPathWithinApplication(request))
				.build();
		var jsonRes = jsonMapper.writeValueAsString(apiError);
		out.write(jsonRes);
		out.flush();
	}

}
