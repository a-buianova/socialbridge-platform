package org.socialbridge.crm.shared;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ProblemDetail> invalid(BadRequestException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::validationError)
                .distinct()
                .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message))
                .toList();
        ProblemDetail body = body(HttpStatus.BAD_REQUEST, "Request validation failed", request);
        body.setProperty("errors", errors);
        return response(HttpStatus.BAD_REQUEST, body, new HttpHeaders());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> mismatch(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter", request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> concurrentModification(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT, "Contact was modified concurrently; reload it and retry", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        // Keep framework HTTP statuses and protocol headers, but never reflect submitted values.
        if (exception instanceof ErrorResponse error) {
            HttpStatusCode status = error.getStatusCode();
            return response(status, body(status, standardDetail(status), request), error.getHeaders());
        }

        // Exception messages and SQL stack traces can contain contact data. Log only a safe
        // source location and exception type, correlated with the response.
        String source = exception.getStackTrace().length == 0
                ? "unknown"
                : exception.getStackTrace()[0].toString();
        LOG.error(
                "Unhandled request failure: exceptionType={} source={} traceId={}",
                exception.getClass().getName(),
                source,
                RequestTracingFilter.traceId(request));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private static ValidationError validationError(FieldError error) {
        String message = error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage();
        return new ValidationError(error.getField(), message);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String detail, HttpServletRequest request) {
        return response(status, body(status, detail, request), new HttpHeaders());
    }

    private static ProblemDetail body(HttpStatusCode status, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("traceId", RequestTracingFilter.traceId(request));
        return body;
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpStatusCode status, ProblemDetail body, HttpHeaders protocolHeaders) {
        return ResponseEntity.status(status)
                .headers(protocolHeaders)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(RequestTracingFilter.HEADER_NAME, (String) body.getProperties().get("traceId"))
                .body(body);
    }

    private static String standardDetail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Invalid request";
            case 404 -> "Resource not found";
            case 405 -> "HTTP method is not supported for this resource";
            case 406 -> "Requested response media type is not supported";
            case 415 -> "Request media type is not supported";
            default -> "The request could not be completed";
        };
    }

    public record ValidationError(String field, String message) {}
}
