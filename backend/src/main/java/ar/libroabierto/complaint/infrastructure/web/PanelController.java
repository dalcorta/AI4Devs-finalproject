package ar.libroabierto.complaint.infrastructure.web;

import ar.libroabierto.complaint.application.CambiarEstadoDeQueja;
import ar.libroabierto.complaint.application.ConsultasDelPanel;
import ar.libroabierto.complaint.application.DatosInvalidosException;
import ar.libroabierto.complaint.application.QuejaNoEncontradaException;
import ar.libroabierto.complaint.application.TransicionNoPermitidaException;
import ar.libroabierto.complaint.domain.EstadoQueja;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** HU-04: bandeja y cambio de estado, solo para el rol FUNCIONARIO (lo exige SeguridadConfig). */
@RestController
class PanelController {

    static final int TAMANIO_PAGINA = 20;

    private final ConsultasDelPanel panel;
    private final CambiarEstadoDeQueja cambiar;

    PanelController(ConsultasDelPanel panel, CambiarEstadoDeQueja cambiar) {
        this.panel = panel;
        this.cambiar = cambiar;
    }

    record Pagina<T>(List<T> items) {
    }

    record CambioRequest(EstadoQueja estado, String comentario) {
    }

    @GetMapping("/api/panel/quejas")
    Pagina<ConsultasDelPanel.ItemDeBandeja> bandeja(@RequestParam(required = false) EstadoQueja estado,
            @RequestParam(defaultValue = "0") int page) {
        if (page < 0) {
            throw new DatosInvalidosException(Map.of("page", "No puede ser negativa"));
        }
        return new Pagina<>(panel.bandeja(estado, page, TAMANIO_PAGINA));
    }

    /** 202: el cambio quedó registrado; su anclaje on-chain es asíncrono. */
    @PatchMapping("/api/quejas/{id}/estado")
    ResponseEntity<Void> cambiarEstado(@AuthenticationPrincipal Jwt token, @PathVariable UUID id,
            @RequestBody CambioRequest pedido) {
        if (pedido.estado() == null) {
            throw new DatosInvalidosException(Map.of("estado", "Es obligatorio"));
        }
        // El actor sale del token firmado, nunca del cuerpo del pedido.
        cambiar.ejecutar(UUID.fromString(token.getSubject()), id, pedido.estado(), pedido.comentario());
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ResponseEntity<Map<String, Object>> datosInvalidos(DatosInvalidosException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Hay datos inválidos", "errores", e.errores()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> pedidoIlegible(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "El pedido tiene un valor no válido"));
    }

    @ExceptionHandler(QuejaNoEncontradaException.class)
    ResponseEntity<Map<String, Object>> noEncontrada(QuejaNoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(TransicionNoPermitidaException.class)
    ResponseEntity<Map<String, Object>> conflicto(TransicionNoPermitidaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", e.getMessage()));
    }
}
