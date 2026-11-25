package com.csit321g1.bairus.lessonfy.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiError(ex.getReason(), ex.getStatusCode().value(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Validation failed.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(message, HttpStatus.BAD_REQUEST.value(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(
                        "Request body is missing or malformed.",
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLargeUpload(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        return badRequest("This file is too large. The limit is 10 MB.", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipartProblem(
            MultipartException ex,
            HttpServletRequest request) {
        return badRequest("The upload could not be read. Please choose a small PDF, Word, PowerPoint, or text file.", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingFile(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {
        return badRequest("Choose a file to convert before continuing.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("Endpoint not found.", HttpStatus.NOT_FOUND.value(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        // Log only a short signature. Request bodies, headers, keys, and document
        // text must never appear in logs.
        log.error("Unhandled {} for {} {}", ex.getClass().getSimpleName(),
                request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Something went wrong. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR.value(), request.getRequestURI()));
    }

    private ResponseEntity<ApiError> badRequest(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(message, HttpStatus.BAD_REQUEST.value(), request.getRequestURI()));
    }

    public record ApiError(String message, int status, String path) {
    }
}
