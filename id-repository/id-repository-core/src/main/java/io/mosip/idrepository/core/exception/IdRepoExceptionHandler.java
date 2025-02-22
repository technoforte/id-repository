package io.mosip.idrepository.core.exception;

import static io.mosip.idrepository.core.constant.IdRepoErrorConstants.AUTHORIZATION_FAILED;
import static io.mosip.idrepository.core.constant.IdRepoErrorConstants.INVALID_INPUT_PARAMETER;
import static io.mosip.idrepository.core.constant.IdRepoErrorConstants.INVALID_REQUEST;
import static io.mosip.idrepository.core.constant.IdRepoErrorConstants.UNKNOWN_ERROR;

import java.nio.file.AccessDeniedException;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.Resource;
import javax.servlet.ServletException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.mosip.idrepository.core.constant.AuthAdapterErrorCode;
import io.mosip.idrepository.core.dto.IdResponseDTO;
import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.core.security.IdRepoSecurityManager;
import io.mosip.idrepository.core.util.EnvUtil;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * The Class IdRepoExceptionHandler - Handler class for all exceptions thrown in
 * Id Repository Idenitty and VID service.
 *
 * @author Manoj SP
 */
@RestControllerAdvice
public class IdRepoExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String REACTIVATE = "reactivate";

	private static final String DEACTIVATE = "deactivate";

	/** The Constant ID_REPO_EXCEPTION_HANDLER. */
	private static final String ID_REPO_EXCEPTION_HANDLER = "IdRepoExceptionHandler";
	
	/** The Constant TIMESTAMP. */
	private static final String REQUEST_TIME = "requesttime";

	/** The Constant ID_REPO. */
	private static final String ID_REPO = "IdRepo";

	/** The Constant READ. */
	private static final String READ = "read";

	/** The Constant CREATE. */
	private static final String CREATE = "create";

	/** The Constant UPDATE. */
	private static final String UPDATE = "update";

	/** The mosip logger. */
	Logger mosipLogger = IdRepoLogger.getLogger(IdRepoExceptionHandler.class);

	/** The id. */
	@Resource
	private Map<String, String> id;

	/**
	 * Handles exceptions that are not handled by other methods in
	 * {@code IdRepoExceptionHandler}.
	 *
	 * @param ex the exception
	 * @param request the request
	 * @return the response entity
	 */
	@ExceptionHandler(Exception.class)
	protected ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleAllExceptions - \n" + ExceptionUtils.getStackTrace(Objects.isNull(rootCause) ? ex : rootCause));
		IdRepoUnknownException e = new IdRepoUnknownException(UNKNOWN_ERROR);
		return new ResponseEntity<>(
				buildExceptionResponse(e, ((ServletWebRequest) request).getHttpMethod(), null),
				HttpStatus.OK);
	}
	
	/**
	 * Handles bean creation exception.{@code BeanCreationException} is handled because
	 * IdObjetMasterDataValidator is loaded lazily and makes use of RestTemplate in
	 * PostConstruct. When RestTemplate throws any exception inside PostConstruct,
	 * it is wrapped as BeanCreationException and thrown by Spring.
	 *
	 * @param ex the ex
	 * @param request the request
	 * @return the response entity
	 */
	@ExceptionHandler(BeanCreationException.class)
	protected ResponseEntity<Object> handleBeanCreationException(BeanCreationException ex, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		rootCause = Objects.isNull(rootCause) ? ex : rootCause;
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleBeanCreationException - \n" + ExceptionUtils.getStackTrace(rootCause));
		if (Objects.nonNull(rootCause) && rootCause.getClass().isAssignableFrom(AuthenticationException.class)) {
			return handleAuthenticationException((AuthenticationException) rootCause, request);
		} else if (Objects.nonNull(rootCause)
				&& rootCause.getClass().isAssignableFrom(IdRepoAppUncheckedException.class)) {
			return handleIdAppUncheckedException((IdRepoAppUncheckedException) rootCause, request);
		} else {
			return handleAllExceptions((Exception) rootCause, request);
		}
	}

	/**
	 * Handle access denied exception thrown when user is not allowed to access the
	 * specific API.
	 *
	 * @param ex
	 *            the ex
	 * @param request
	 *            the request
	 * @return the response entity
	 */
	@ExceptionHandler(AccessDeniedException.class)
	protected ResponseEntity<Object> handleAccessDeniedException(Exception ex, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleAccessDeniedException - \n" + ExceptionUtils.getStackTrace(Objects.isNull(rootCause) ? ex : rootCause));
		IdRepoUnknownException e = new IdRepoUnknownException(AUTHORIZATION_FAILED);
		return new ResponseEntity<>(
				buildExceptionResponse(e, ((ServletWebRequest) request).getHttpMethod(), null),
				HttpStatus.OK);
	}

	/**
	 * Handle authentication exception - thrown in {@code RestHelper} when an 
	 * application fails to authenticate the rest request.
	 *
	 * @param ex the ex
	 * @param request the request
	 * @return the response entity
	 */
	@ExceptionHandler(AuthenticationException.class)
	protected ResponseEntity<Object> handleAuthenticationException(@NonNull AuthenticationException ex, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleAuthenticationException - \n" + ExceptionUtils.getStackTrace(Objects.isNull(rootCause) ? ex : rootCause));
		IdRepoUnknownException e = new IdRepoUnknownException(
				ex.getErrorTexts().isEmpty() ? AuthAdapterErrorCode.UNAUTHORIZED.getErrorCode() : ex.getErrorCode(),
				ex.getErrorTexts().isEmpty() ? AuthAdapterErrorCode.UNAUTHORIZED.getErrorMessage() : ex.getErrorText());
		return new ResponseEntity<>(
				buildExceptionResponse(e, ((ServletWebRequest) request).getHttpMethod(), null),
				ex.getStatusCode() == 0 ? HttpStatus.UNAUTHORIZED : HttpStatus.valueOf(ex.getStatusCode()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.web.servlet.mvc.method.annotation.
	 * ResponseEntityExceptionHandler#handleExceptionInternal(java.lang.Exception,
	 * java.lang.Object, org.springframework.http.HttpHeaders,
	 * org.springframework.http.HttpStatus,
	 * org.springframework.web.context.request.WebRequest)
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object errorMessage,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleExceptionInternal - \n" + ExceptionUtils.getStackTrace(Objects.isNull(rootCause) ? ex : rootCause));
		if (ex instanceof HttpMessageNotReadableException && org.apache.commons.lang3.exception.ExceptionUtils
				.getRootCause(ex).getClass().isAssignableFrom(DateTimeParseException.class)) {
			ex = new IdRepoAppException(INVALID_INPUT_PARAMETER.getErrorCode(),
					String.format(INVALID_INPUT_PARAMETER.getErrorMessage(), REQUEST_TIME));
			if (request instanceof ServletWebRequest
					&& ((ServletWebRequest) request).getRequest().getRequestURI().endsWith(DEACTIVATE)) {
				return new ResponseEntity<>(
						buildExceptionResponse(ex, ((ServletWebRequest) request).getHttpMethod(), DEACTIVATE),
						HttpStatus.OK);
			} else if (request instanceof ServletWebRequest
					&& ((ServletWebRequest) request).getRequest().getRequestURI().endsWith(REACTIVATE)) {
				return new ResponseEntity<>(
						buildExceptionResponse(ex, ((ServletWebRequest) request).getHttpMethod(), REACTIVATE),
						HttpStatus.OK);
			} else {
				return new ResponseEntity<>(
						buildExceptionResponse(ex, ((ServletWebRequest) request).getHttpMethod(), null), HttpStatus.OK);
			}
		} else if (ex instanceof HttpMessageNotReadableException || ex instanceof ServletException
				|| ex instanceof BeansException) {
			ex = new IdRepoAppException(INVALID_REQUEST.getErrorCode(),
					INVALID_REQUEST.getErrorMessage());

			return new ResponseEntity<>(buildExceptionResponse(ex, ((ServletWebRequest) request).getHttpMethod(), null),
					HttpStatus.OK);
		} else {
			return handleAllExceptions(ex, request);
		}
	}

	/**
	 * Handle id app exception - handle {@code IdRepoAppException} thrown from 
	 * application.
	 *
	 * @param ex the ex
	 * @param request the request
	 * @return the response entity
	 */
	@ExceptionHandler(IdRepoAppException.class)
	protected ResponseEntity<Object> handleIdAppException(@NonNull IdRepoAppException ex, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleIdAppException - \n" + ExceptionUtils.getStackTrace(Objects.isNull(rootCause) ? ex : rootCause));

		return new ResponseEntity<>(buildExceptionResponse(ex,
				((ServletWebRequest) request).getHttpMethod(), ex.getOperation()), HttpStatus.OK);
	}

	/**
	 * Handle id app unchecked exception - handle {@code IdRepoAppUncheckedException} thrown from 
	 * application..
	 *
	 * @param ex the ex
	 * @param request the request
	 * @return the response entity
	 */
	@ExceptionHandler(IdRepoAppUncheckedException.class)
	protected ResponseEntity<Object> handleIdAppUncheckedException(@NonNull IdRepoAppUncheckedException ex, WebRequest request) {
		Throwable rootCause = getRootCause(ex);
		mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO, ID_REPO_EXCEPTION_HANDLER,
				"handleIdAppUncheckedException - \n" + ExceptionUtils.getStackTrace(Objects.isNull(rootCause) ? ex : rootCause));

		return new ResponseEntity<>(
				buildExceptionResponse(ex, ((ServletWebRequest) request).getHttpMethod(), null),
				HttpStatus.OK);
	}

	/**
	 * Constructs exception response body for all exceptions.
	 *
	 * @param ex the ex
	 * @param httpMethod the http method
	 * @param operation the operation
	 * @return the object
	 */
	private Object buildExceptionResponse(Exception ex, @Nullable HttpMethod httpMethod, String operation) {

		IdResponseDTO response = new IdResponseDTO();

		Throwable e = getIdRepoAppExceptionRootCause(ex);

		if (Objects.nonNull(operation)) {
			response.setId(id.get(operation));
		} else if (Objects.nonNull(httpMethod)) {
			if (httpMethod.compareTo(HttpMethod.GET) == 0) {
				response.setId(id.get(READ));
			} else if (httpMethod.compareTo(HttpMethod.POST) == 0) {
				response.setId(id.get(CREATE));
			} else if (httpMethod.compareTo(HttpMethod.PATCH) == 0) {
				response.setId(id.get(UPDATE));
			}
		}

		response.setErrors(getAllErrors(e));

		response.setVersion(EnvUtil.getAppVersion());

		return response;
	}

	public static List<ServiceError> getAllErrors(Throwable e) {
		List<ServiceError> errors = null;
		if (e instanceof BaseCheckedException) {
			List<String> errorCodes = ((BaseCheckedException) e).getCodes();
			List<String> errorTexts = ((BaseCheckedException) e).getErrorTexts();

			errors = errorTexts.parallelStream()
					.map(errMsg -> new ServiceError(errorCodes.get(errorTexts.indexOf(errMsg)), errMsg)).distinct()
					.collect(Collectors.toList());

		}

		if (e instanceof BaseUncheckedException) {
			List<String> errorCodes = ((BaseUncheckedException) e).getCodes();
			List<String> errorTexts = ((BaseUncheckedException) e).getErrorTexts();

			errors = errorTexts.parallelStream()
					.map(errMsg -> new ServiceError(errorCodes.get(errorTexts.indexOf(errMsg)), errMsg)).distinct()
					.collect(Collectors.toList());

		}
		
		return errors;
	}

	private Throwable getRootCause(Exception ex) {
		Throwable rootCause;
		try {
			rootCause = ExceptionUtils.getRootCause(ex);
		} catch (Exception e) {
			mosipLogger.warn("Exception thrown from ExceptionUtils when finding root cause : " + e.getMessage());
			rootCause = getIdRepoAppExceptionRootCause(ex);
		}
		return rootCause;
	}

	/**
	 * Gets the root cause.
	 *
	 * @param ex the ex
	 * @return the root cause
	 */
	private Throwable getIdRepoAppExceptionRootCause(Exception ex) {
		Throwable e = ex;
		while (e != null) {
			if (Objects.nonNull(e.getCause()) && (e.getCause() instanceof IdRepoAppException)) {
				e = e.getCause();
			} else {
				break;
			}
		}
		return e;
	}
}
