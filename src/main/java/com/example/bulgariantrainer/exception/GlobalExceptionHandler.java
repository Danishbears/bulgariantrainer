package com.example.bulgariantrainer.exception;

import com.example.bulgariantrainer.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.ErrorResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SentenceException.class)
    ResponseEntity<ApiError> sentenceFailure(SentenceException exception) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }
    @ExceptionHandler(TypingException.class)
    ResponseEntity<ApiError> typingFailure(TypingException exception) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FlashcardException.class)
    ResponseEntity<ApiError> flashcardFailure(FlashcardException exception) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ImportException.class)
    ResponseEntity<ApiError> importFailure(ImportException exception) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(Exception exception) {
        return error(413, "FILE_TOO_LARGE", "Maximum file size is 1 MiB", Map.of());
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ResponseEntity<ApiError> malformedUpload(Exception exception) {
        return error(400, "INVALID_UPLOAD", "A valid multipart file upload is required", Map.of());
    }

    @ExceptionHandler(WordNotFoundException.class)
    ResponseEntity<ApiError> notFound(WordNotFoundException exception) {
        return error(404, "WORD_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(VocabularyConflictException.class)
    ResponseEntity<ApiError> conflict(VocabularyConflictException exception) {
        return error(409, exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidVocabularyException.class)
    ResponseEntity<ApiError> invalid(InvalidVocabularyException exception) {
        return error(400, "INVALID_REQUEST", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(400, "VALIDATION_FAILED", "Request validation failed", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    ResponseEntity<ApiError> malformed(Exception exception) {
        return error(400, "INVALID_REQUEST", "Invalid request body or query parameters", Map.of());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiError> integrity(Exception exception) {
        return error(409, "DATA_CONFLICT",
                "The operation conflicts with existing data. No changes were saved.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            int status = response.getStatusCode().value();
            return error(status, "HTTP_ERROR", HttpStatus.valueOf(status).getReasonPhrase(), Map.of());
        }
        log.error("Unexpected request failure", exception);
        return error(500, "INTERNAL_ERROR", "An unexpected error occurred", Map.of());
    }

    private ResponseEntity<ApiError> error(int status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status, code, message, fields, Instant.now()));
    }
}

