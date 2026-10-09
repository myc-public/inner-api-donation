package ma.myc.inner.donation.config;

import ma.myc.inner.donation.exception.DonorAlreadyExistsException;
import ma.myc.inner.donation.exception.DonorProfileRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.util.UrlPathHelper;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import ma.myc.inner.donation.domain.core.error.ApiError;
import ma.myc.inner.donation.domain.core.error.ApiErrorItem;
import ma.myc.inner.donation.exception.BusinessException;
import ma.myc.inner.donation.exception.NotFoundException;
import ma.myc.inner.donation.exception.TechnicalException;
import ma.myc.inner.donation.util.component.MsgSource;
import ma.myc.inner.donation.util.component.TraceRequestHandler;
import ma.myc.inner.donation.util.constants.ErrorConstants;

@RestControllerAdvice
@AllArgsConstructor
public class ErrorHandlingAdvice {
	private static final Logger logger = LoggerFactory.getLogger(ErrorHandlingAdvice.class);
	private static final String LOG_REQUEST = "{} : {} {} ";
	public static final String BUSINESS_EXCEPTION_HAS_OCCURRED = "Business exception {} has occurred";
	public static final String INTER_COMMUNICATION_EXCEPTION_HAS_OCCURRED = "Inter exception {} has occurred";

	private final MsgSource messageSource;
	private final UrlPathHelper urlPathHelper = new UrlPathHelper();
	private final TraceRequestHandler requestHandler;

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	ApiError onAccessDeniedException(AccessDeniedException e, final HttpServletRequest request) {
		String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		logger.error(LOG_REQUEST, message, request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_SECURITY_FORBIDDEN)
				.title(HttpStatus.FORBIDDEN.getReasonPhrase())
				.detail("Access to this resource is denied")
				.status(HttpStatus.FORBIDDEN.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler(AuthenticationException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	ApiError onAuthenticationException(AuthenticationException e, final HttpServletRequest request) {
		String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		logger.error(LOG_REQUEST, message, request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();
		return ApiError.builder()
				.type(ErrorConstants.URI_SECURITY_AUTHORIZED)
				.title(HttpStatus.UNAUTHORIZED.getReasonPhrase())
				.detail("Access to this resource is denied")
				.status(HttpStatus.UNAUTHORIZED.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler({ TechnicalException.class, RuntimeException.class })
	@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
	ApiError onTechnicalException(Exception e, final HttpServletRequest request) {
		logger.error(e.getMessage(), e);
		String traceId = requestHandler.getCorrelationId();
		String message = messageSource.getMessage(ErrorConstants.ERR_TECHNICAL, traceId);
		if (StringUtils.startsWithIgnoreCase(message, "!")) {
			message = "Unexpected technical error has issued. Please provide your administrator with the token "
					+ traceId + " for more details";
		}
		return ApiError.builder()
				.title("Technical Error")
				.detail(message)
				.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.type(ErrorConstants.URI_UNEXPECTED_TECHNICAL_ERROR)
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler(BusinessException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onBusinessException(BusinessException e, final HttpServletRequest request) {
		logger.info(BUSINESS_EXCEPTION_HAS_OCCURRED, e.getMessage());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_BUSINESS_EXCEPTION)
				.title("Business exception")
				.detail("Business exception has occurred")
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.errors(e.getMessages()
						.stream()
						.map(el -> {
							var errorMessage = getErrorMessage(el.getMessage(), el.getParams());
							return ApiErrorItem.builder()
									.target(el.getTarget())
									.message(errorMessage)
									.build();
						})
						.toList())
				.build();
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onMissingServletRequestParameterException(
			MissingServletRequestParameterException e, final HttpServletRequest request) {
		String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		logger.error(LOG_REQUEST, message, request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_MISSING_REQUEST_PARAMETER)
				.title("Missing Request Parameter")
				.detail(e.getMessage())
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onMissingServletRequestPartException(MissingServletRequestPartException e,
			final HttpServletRequest request) {
		String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		logger.error(LOG_REQUEST, message, request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_MISSING_REQUEST_PARAMETER)
				.title("Missing Request Part")
				.detail(e.getMessage())
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	ApiError onHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e,
			final HttpServletRequest request) {
		String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		logger.error(LOG_REQUEST, message, request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_METHOD_NOT_ALLOWED)
				.title("Request method not supported")
				.detail(e.getMessage())
				.status(HttpStatus.METHOD_NOT_ALLOWED.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler(ConstraintViolationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onConstraintValidationException(
			ConstraintViolationException e, final HttpServletRequest request) {
		String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		logger.error(LOG_REQUEST, message, request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_VALIDATION_CONSTRAINT_VIOLATION)
				.title("Field validation").detail(e.getMessage())
				.detail(e.getMessage())
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.errors(e.getConstraintViolations().stream()
						.map(violation -> ApiErrorItem.builder()
								.target(violation.getPropertyPath().toString())
								.message(violation.getMessage()).build())
						.toList())
				.build();
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onMethodArgumentNotValidException(
			MethodArgumentNotValidException e, final HttpServletRequest request) {
		// Le message Spring contient les valeurs rejetees (donnees personnelles) : on ne journalise que les champs
		var fields = e.getBindingResult().getFieldErrors().stream().map(FieldError::getField).toList();
		logger.warn(LOG_REQUEST, "Request validation failed on fields " + fields, request.getMethod(),
				request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_METHOD_ARGUMENT_NOT_VALID)
				.title("Method Argument Not Valid")
				.detail("Request validation failed")
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.errors(e.getBindingResult().getFieldErrors().stream()
						.map(fieldError -> ApiErrorItem.builder()
								.target(fieldError.getField())
								.message(fieldError.getDefaultMessage())
								.build())
						.toList())
				.build();
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onHttpMessageNotReadableException(HttpMessageNotReadableException e,
			final HttpServletRequest request) {
		// Le message Jackson peut reprendre le contenu du corps (donnees personnelles) : seul le type d'erreur est journalise
		String cause = e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName();
		logger.warn(LOG_REQUEST, "Unreadable request body (" + cause + ")", request.getMethod(), request.getRequestURI());
		String traceId = requestHandler.getCorrelationId();

		return ApiError.builder()
				.type(ErrorConstants.URI_HTTP_MESSAGE_NOT_READABLE)
				.title("Malformed request body")
				.detail("The request body is missing or is not valid JSON for this operation")
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ApiError onNotFoundException(NotFoundException e, final HttpServletRequest request) {
		logger.info("Resource not found: {}", e.getMessage());
		String traceId = requestHandler.getCorrelationId();
		return ApiError.builder()
				.type(ErrorConstants.URI_NOT_FOUND)
				.title("Not Found")
				.detail(e.getMessage())
				.status(HttpStatus.NOT_FOUND.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

	// Add your ExceptionHandler here ...

	private String getErrorMessage(String code, Object... params) {
		String message = messageSource.getMessage(code, params);
		if (StringUtils.startsWithIgnoreCase(message, "!")) {
			message = code;
		}
		return message;
	}
	// Add your ExceptionHandler here ...
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError onMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
			final HttpServletRequest request) {
		// Sans ce handler, un identifiant mal forme (ex. UUID invalide) tombait dans RuntimeException -> 500
		String expectedType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "value";
		logger.warn(LOG_REQUEST, "Invalid value for parameter '" + e.getName() + "'", request.getMethod(),
				request.getRequestURI());
		return ApiError.builder()
				.type(ErrorConstants.URI_ARGUMENT_TYPE_MISMATCH)
				.title("Invalid parameter")
				.detail("Parameter '" + e.getName() + "' has an invalid format")
				.status(HttpStatus.BAD_REQUEST.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(requestHandler.getCorrelationId())
				.errors(List.of(ApiErrorItem.builder()
						.target(e.getName())
						.message("must be a valid " + expectedType)
						.build()))
				.build();
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	ApiError onHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e,
			final HttpServletRequest request) {
		logger.warn(LOG_REQUEST, "Unsupported content type " + e.getContentType(), request.getMethod(),
				request.getRequestURI());
		return ApiError.builder()
				.type(ErrorConstants.URI_UNSUPPORTED_MEDIA_TYPE)
				.title("Unsupported Media Type")
				.detail("Supported content types: " + e.getSupportedMediaTypes())
				.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(requestHandler.getCorrelationId())
				.build();
	}

	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
	ApiError onHttpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException e,
			final HttpServletRequest request) {
		// Si le client n'accepte pas JSON, ce corps ne peut pas etre ecrit : Spring renvoie alors un 406 sans corps
		logger.warn(LOG_REQUEST, "Not acceptable", request.getMethod(), request.getRequestURI());
		return ApiError.builder()
				.type(ErrorConstants.URI_NOT_ACCEPTABLE)
				.title("Not Acceptable")
				.detail("Supported response types: " + e.getSupportedMediaTypes())
				.status(HttpStatus.NOT_ACCEPTABLE.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(requestHandler.getCorrelationId())
				.build();
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	ApiError onDataIntegrityViolationException(DataIntegrityViolationException e, final HttpServletRequest request) {
		// Le message SQL reprend les valeurs en conflit (ex. l'email) : jamais expose ni journalise
		logger.warn(LOG_REQUEST, "Data integrity violation (" + e.getMostSpecificCause().getClass().getSimpleName() + ")",
				request.getMethod(), request.getRequestURI());
		return ApiError.builder()
				.type(ErrorConstants.URI_CONFLICT)
				.title("Conflict")
				.detail("The request conflicts with existing data")
				.status(HttpStatus.CONFLICT.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(requestHandler.getCorrelationId())
				.build();
	}

	@ExceptionHandler({ DonorAlreadyExistsException.class, DonorProfileRequiredException.class })
	@ResponseStatus(HttpStatus.CONFLICT)
	ApiError onDonorAlreadyExistsException(RuntimeException e, final HttpServletRequest request) {
		logger.info("Conflict: {}", e.getMessage());
		String traceId = requestHandler.getCorrelationId();
		return ApiError.builder()
				.type(ErrorConstants.URI_CONFLICT)
				.title("Conflict")
				.detail(e.getMessage())
				.status(HttpStatus.CONFLICT.value())
				.instance(urlPathHelper.getPathWithinApplication(request))
				.traceId(traceId)
				.build();
	}

}
