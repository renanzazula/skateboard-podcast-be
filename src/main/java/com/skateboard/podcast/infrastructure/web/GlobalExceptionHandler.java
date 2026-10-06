package com.skateboard.podcast.infrastructure.web;

import com.skateboard.application.dto.ErrorResponse;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException;
import com.skateboard.podcast.domain.exception.GuestApplicationNotFoundException;
import com.skateboard.podcast.domain.exception.PostNotFoundException;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Method-level security (@PreAuthorize) denials surface here as
    // AccessDeniedException; without this handler every permission denial
    // would fall through to the generic 500 handler below.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(CategoryNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(GuestApplicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGuestApplicationNotFound(GuestApplicationNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateActiveApplicationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateActiveApplication(DuplicateActiveApplicationException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    // A failed @Valid @RequestBody (e.g. a required field left out) — without
    // this it falls through to the generic 500 handler below.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("Invalid request");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // Malformed request JSON — a value that can't parse into its target type
    // (e.g. a non-URI string in CreateGuestApplicationRequest.socialLinks, or
    // an unknown enum value), not valid-but-missing. Without this it falls
    // through to the generic 500 handler below.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    // A failed constraint on a query/path parameter (e.g. search over its
    // spec maxLength). The generated interfaces are @Validated, so depending on
    // whether the controller is proxied this arrives as either type — both are
    // the caller's mistake, not a 500.
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleParameterValidation(Exception ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request parameter");
    }

    // A controller-thrown ResponseStatusException (e.g. "no guest application
    // for this user" on first read) carries its own status/reason — without
    // this it falls through to the generic 500 handler below, turning an
    // expected "not found" into a false alarm.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return buildResponse(status, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse error = new ErrorResponse()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(OffsetDateTime.now());
        return ResponseEntity.status(status).body(error);
    }
}
