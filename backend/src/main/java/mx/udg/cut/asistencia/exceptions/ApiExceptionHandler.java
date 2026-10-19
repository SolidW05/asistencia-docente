package mx.udg.cut.asistencia.exceptions;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        FieldError field = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "code",
                                "VALIDATION_ERROR",
                                "message",
                                field == null
                                        ? "Los datos enviados no son válidos"
                                        : field.getDefaultMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, String>> constraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "code",
                                "VALIDATION_ERROR",
                                "message",
                                "Los datos enviados no son válidos"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "code",
                                "CONFLICT",
                                "message",
                                "La solicitud entra en conflicto con datos existentes"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> optimistic(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "code",
                                "STALE_VERSION",
                                "message",
                                "Los datos cambiaron mientras se guardaban. Actualiza la página e inténtalo de nuevo."));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    ResponseEntity<Map<String, String>> malformed(Exception ex) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "code",
                                "INVALID_REQUEST",
                                "message",
                                "La solicitud contiene un valor ausente o con formato incorrecto."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        Map.of(
                                "code",
                                "FORBIDDEN",
                                "message",
                                "No tienes permiso para realizar esta acción"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unknown(Exception ex) {
        log.error("Unhandled API exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        Map.of(
                                "code",
                                "INTERNAL_ERROR",
                                "message",
                                "No se pudo completar la solicitud"));
    }
}
