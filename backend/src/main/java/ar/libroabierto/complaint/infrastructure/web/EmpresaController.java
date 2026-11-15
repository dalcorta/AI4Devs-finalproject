package ar.libroabierto.complaint.infrastructure.web;

import ar.libroabierto.complaint.application.ConsultasDeEmpresa;
import ar.libroabierto.complaint.application.ConsultasPublicas;
import ar.libroabierto.complaint.application.DatosInvalidosException;
import ar.libroabierto.complaint.application.QuejaNoEncontradaException;
import ar.libroabierto.complaint.application.ResponderQueja;
import ar.libroabierto.complaint.application.RespuestaNoDisponibleException;
import ar.libroabierto.complaint.application.RespuestaNoPermitidaException;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** HU-05: bandeja y respuesta de la empresa, solo para el rol EMPRESA (lo exige SeguridadConfig). */
@RestController
class EmpresaController {

    static final int TAMANIO_PAGINA = 20;

    private final ConsultasDeEmpresa consultas;
    private final ResponderQueja responder;

    EmpresaController(ConsultasDeEmpresa consultas, ResponderQueja responder) {
        this.consultas = consultas;
        this.responder = responder;
    }

    record Bandeja(ConsultasPublicas.EmpresaResumen empresa, List<ConsultasDeEmpresa.QuejaRecibida> items) {
    }

    record RespuestaRequest(String texto) {
    }

    record RespuestaCreada(UUID id) {
    }

    @GetMapping("/api/empresa/quejas")
    ResponseEntity<Bandeja> bandeja(@AuthenticationPrincipal Jwt token, @RequestParam(defaultValue = "0") int page) {
        if (page < 0) {
            throw new DatosInvalidosException(Map.of("page", "No puede ser negativa"));
        }
        UUID usuario = UUID.fromString(token.getSubject());
        return consultas.empresaDe(usuario)
                .map(empresa -> ResponseEntity.ok(
                        new Bandeja(empresa, consultas.quejasRecibidas(usuario, page, TAMANIO_PAGINA))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /** 202: la respuesta quedó registrada; su publicación en IPFS y su anclaje son asíncronos. */
    @PostMapping("/api/quejas/{id}/respuesta")
    ResponseEntity<RespuestaCreada> responder(@AuthenticationPrincipal Jwt token, @PathVariable UUID id,
            @RequestBody RespuestaRequest pedido) {
        // Quién responde (y por qué empresa) sale del token firmado, nunca del cuerpo del pedido.
        UUID respuesta = responder.ejecutar(UUID.fromString(token.getSubject()), id, pedido.texto());
        return ResponseEntity.accepted().body(new RespuestaCreada(respuesta));
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

    @ExceptionHandler(RespuestaNoPermitidaException.class)
    ResponseEntity<Map<String, Object>> noPermitida(RespuestaNoPermitidaException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(RespuestaNoDisponibleException.class)
    ResponseEntity<Map<String, Object>> conflicto(RespuestaNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", e.getMessage()));
    }
}
