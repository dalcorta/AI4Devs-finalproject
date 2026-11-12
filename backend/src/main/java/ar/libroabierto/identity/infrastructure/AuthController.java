package ar.libroabierto.identity.infrastructure;

import ar.libroabierto.identity.application.Autenticar;
import ar.libroabierto.identity.application.CredencialesInvalidasException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final Autenticar autenticar;

    AuthController(Autenticar autenticar) {
        this.autenticar = autenticar;
    }

    record LoginRequest(String email, String password) {
    }

    @PostMapping("/login")
    Autenticar.Sesion login(@RequestBody LoginRequest pedido) {
        return autenticar.ejecutar(pedido.email(), pedido.password());
    }

    // No se registra el email: es un dato personal y no debe terminar en los logs.
    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<Map<String, String>> credencialesInvalidas(CredencialesInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", e.getMessage()));
    }
}
