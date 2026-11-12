package ar.libroabierto.complaint.infrastructure.web;

import ar.libroabierto.complaint.application.NuevaQueja;
import ar.libroabierto.complaint.application.RegistrarQueja;
import ar.libroabierto.complaint.domain.AnchorStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quejas")
class QuejaController {

    private final RegistrarQueja registrar;

    QuejaController(RegistrarQueja registrar) {
        this.registrar = registrar;
    }

    record QuejaRequest(String cuitEmpresa, String razonSocial, String categoria, String descripcion,
            BigDecimal montoReclamado, LocalDate fechaHecho) {
    }

    record QuejaAceptada(UUID id, AnchorStatus anchorStatus) {
    }

    /** 202: la queja quedó registrada; publicarla y anclarla es asíncrono. */
    @PostMapping
    ResponseEntity<QuejaAceptada> registrar(@AuthenticationPrincipal Jwt token, @RequestBody QuejaRequest pedido) {
        // El denunciante sale del token firmado, nunca del cuerpo del pedido.
        UUID id = registrar.ejecutar(new NuevaQueja(UUID.fromString(token.getSubject()), pedido.cuitEmpresa(),
                pedido.razonSocial(), pedido.categoria(), pedido.descripcion(), pedido.montoReclamado(),
                pedido.fechaHecho()));
        return ResponseEntity.accepted()
                .location(URI.create("/api/quejas/" + id))
                .body(new QuejaAceptada(id, AnchorStatus.PENDIENTE));
    }
}
