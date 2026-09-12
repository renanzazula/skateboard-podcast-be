package com.skateboard.podcast.infrastructure.web;

import com.skateboard.application.dto.ErrorResponse;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every exception this handler maps must come back as the generated
 * ErrorResponse shape (status/error/message/timestamp) — see CLAUDE.md,
 * "Errors". The 500 branch is the one with a real failure mode worth
 * asserting against: leaking an internal exception message to a client.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAccessDeniedTo403WithAGenericMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getError()).isEqualTo("Forbidden");
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void mapsPostNotFoundTo404CarryingTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handlePostNotFound(new PostNotFoundException("post-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Post not found: post-123");
    }

    @Test
    void mapsCategoryNotFoundTo404CarryingTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleCategoryNotFound(new CategoryNotFoundException("interviews"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Category not found: interviews");
    }

    @Test
    void mapsIllegalArgumentTo400CarryingTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new IllegalArgumentException("slug must not be blank"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("slug must not be blank");
    }

    @Test
    void mapsTypeMismatchTo400NamingTheOffendingParameter() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", java.util.UUID.class, "id", dummyMethodParameter(), new IllegalArgumentException());

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid value for parameter 'id'");
    }

    @Test
    void mapsValidationFailureTo400UsingTheFirstFieldError() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createPostRequest");
        bindingResult.addError(new FieldError("createPostRequest", "title", "must not be blank"));
        bindingResult.addError(new FieldError("createPostRequest", "slug", "must not be blank"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("title must not be blank");
    }

    @Test
    void mapsValidationFailureWithNoFieldErrorsToADefaultMessage() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createPostRequest");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid request");
    }

    /**
     * The important behaviour here is negative: whatever the real exception
     * says must never reach the client on a 500, only the generic message.
     */
    @Test
    void mapsAnyOtherExceptionTo500WithoutLeakingItsMessage() {
        RuntimeException sensitive = new RuntimeException("db password is hunter2; connection string leaked");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(sensitive);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
        assertThat(response.getBody().getMessage()).isNotEqualTo(sensitive.getMessage());
    }

    private MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String arg) {
        // Only exists to give MethodParameter a real Method to point at.
    }
}
