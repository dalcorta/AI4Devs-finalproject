package ar.libroabierto.complaint.infrastructure.web;

import ar.libroabierto.complaint.application.DatosInvalidosException;
import ar.libroabierto.complaint.application.DenuncianteNoHabilitadoException;
import ar.libroabierto.complaint.application.LimiteDiarioAlcanzadoException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = QuejaController.class)
class ErroresDeQuejas {

    @ExceptionHandler(DatosInvalidosException.class)
    ResponseEntity<Map<String, Object>> datosInvalidos(DatosInvalidosException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Hay datos inválidos", "errores", e.errores()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> jsonIlegible(HttpMessageNotReadableException e) {
        // No se devuelve el detalle de Jackson: puede incluir fragmentos del cuerpo enviado.
        return ResponseEntity.badRequest().body(Map.of("mensaje", "El cuerpo no es un JSON válido para una queja"));
    }

    @ExceptionHandler(DenuncianteNoHabilitadoException.class)
    ResponseEntity<Map<String, Object>> noHabilitado(DenuncianteNoHabilitadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(LimiteDiarioAlcanzadoException.class)
    ResponseEntity<Map<String, Object>> limiteDiario(LimiteDiarioAlcanzadoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("mensaje", e.getMessage()));
    }
}
