package com.tabularai.exception;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityNotFoundException;

/**
 * Manejador global de errores que intercepta las excepciones de toda la aplicación
 * y las transforma en respuestas JSON estandarizadas bajo la norma RFC 7807 (ProblemDetail).
 */
@Hidden
@RestControllerAdvice(basePackages = "com.tabularai.controller")
public class GlobalExceptionHandler {

    // 1. Errores de validación del archivo CSV (400 Bad Request)
    @ExceptionHandler(InvalidCsvException.class)
    public ProblemDetail handleInvalidCsv(InvalidCsvException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Archivo CSV inválido");
        problem.setType(URI.create("https://tabularai.com/errors/invalid-csv"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // 2. Recurso no encontrado en la base de datos (404 Not Found)
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        problem.setType(URI.create("https://tabularai.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // 3. Errores en validaciones de DTOs con @Valid (400 Bad Request)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Uno o más campos de la petición contienen errores de validación."
        );
        problem.setTitle("Error de validación");
        problem.setType(URI.create("https://tabularai.com/errors/validation-error"));
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // 4. Archivo que supera el límite de 5 MB configurado en Spring (413 Payload Too Large)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxSizeException(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido de 5 MB."
        );
        problem.setTitle("Archivo demasiado grande");
        problem.setType(URI.create("https://tabularai.com/errors/file-too-large"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // 5. Falta el archivo adjunto 'file' en la petición multipart (400 Bad Request)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingFilePart(MissingServletRequestPartException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Debes adjuntar un archivo CSV en el parámetro '" + ex.getRequestPartName() + "'."
        );
        problem.setTitle("Parámetro requerido faltante");
        problem.setType(URI.create("https://tabularai.com/errors/missing-part"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // 6. Cualquier otro error inesperado (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado al procesar la solicitud: " + ex.getMessage()
        );
        problem.setTitle("Error interno del servidor");
        problem.setType(URI.create("https://tabularai.com/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
