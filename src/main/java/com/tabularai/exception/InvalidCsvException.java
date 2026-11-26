package com.tabularai.exception;

/**
 * Excepción personalizada que se lanza cuando el archivo CSV subido no cumple
 * con las reglas de validación (vacío, extensión inválida, exceso de peso o tope de filas).
 */
public class InvalidCsvException extends RuntimeException {

    public InvalidCsvException(String message) {
        super(message);
    }

    public InvalidCsvException(String message, Throwable cause) {
        super(message, cause);
    }
}
