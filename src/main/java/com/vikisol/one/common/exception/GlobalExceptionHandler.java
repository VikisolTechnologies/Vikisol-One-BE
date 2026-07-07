package com.vikisol.one.common.exception;

import com.vikisol.one.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(false, "Invalid email or password", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, "Access denied", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        String readable = errors.entrySet().stream()
                .map(e -> humanizeField(e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, readable.isBlank() ? "Please check the highlighted fields" : readable, errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("'%s' is not a valid value for %s", ex.getValue(), humanizeField(ex.getName()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, message, null));
    }

    // Malformed request bodies - most commonly an invalid date (e.g. a stray digit typed into a
    // date picker producing "52022-02-25") - land here instead of a raw Jackson stack trace.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String message = "One of the fields you entered isn't in a valid format";
        Throwable cause = ex.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().contains("LocalDate")) {
            message = "Please enter a valid date";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, message, null));
    }

    // A raw Postgres unique-violation ("duplicate key value violates unique constraint ...") must
    // never reach the frontend as literal SQL text. Translate the constraint/column name into a
    // plain-English message where recognizable, falling back to a generic one otherwise. This is
    // the single point of enforcement - every module's create/update paths funnel through here
    // rather than needing their own try/catch around every save().
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String raw = (ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        log.warn("Data integrity violation: {}", raw);
        String lower = raw != null ? raw.toLowerCase() : "";
        String message;
        if (lower.contains("email")) {
            message = "This email address is already in use.";
        } else if (lower.contains("employee_id") || lower.contains("employeeid")) {
            message = "This Employee ID is already in use.";
        } else if (lower.contains("user_id") || lower.contains("userid")) {
            message = "This account is already linked to another employee record.";
        } else if (lower.contains("phone") || lower.contains("mobile")) {
            message = "This phone number is already in use.";
        } else if (lower.contains("pan")) {
            message = "This PAN number is already in use.";
        } else if (lower.contains("aadhar") || lower.contains("aadhaar")) {
            message = "This Aadhaar number is already in use.";
        } else if (lower.contains("unique") || lower.contains("duplicate")) {
            message = "One of the values you entered is already in use elsewhere in the system.";
        } else {
            message = "This request could not be completed because it conflicts with existing data.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, message, null));
    }

    // Every business-rule check in the service layer throws a plain RuntimeException with a
    // human-readable message (e.g. "Already checked in for today", "Department not found").
    // Surface that message directly instead of letting it fall through to a generic 500.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.warn("Request rejected: {}", ex.getMessage());
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "This request could not be processed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, "Something went wrong on our end. Please try again.", null));
    }

    private String humanizeField(String field) {
        String spaced = field.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase();
    }
}
