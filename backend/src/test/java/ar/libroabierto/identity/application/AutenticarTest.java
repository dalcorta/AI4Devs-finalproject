package ar.libroabierto.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.identity.domain.Rol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticarTest {

    private final UUID usuario = UUID.randomUUID();
    private HasherFalso hasher;
    private Autenticar autenticar;

    @BeforeEach
    void setUp() {
        Map<String, Cuenta> cuentas = new HashMap<>();
        cuentas.put("ciudadana@example.test", new Cuenta(usuario, "hash(correcta)", Rol.CIUDADANO));
        hasher = new HasherFalso();
        autenticar = new Autenticar(email -> Optional.ofNullable(cuentas.get(email)), hasher,
                (id, rol) -> "token-" + id + "-" + rol);
    }

    @Test
    void conCredencialesCorrectasDevuelveElTokenYElRol() {
        Autenticar.Sesion sesion = autenticar.ejecutar("ciudadana@example.test", "correcta");

        assertThat(sesion).isEqualTo(new Autenticar.Sesion("token-" + usuario + "-CIUDADANO", Rol.CIUDADANO));
    }

    @Test
    void elEmailSeComparaSinMayusculasNiEspacios() {
        assertThat(autenticar.ejecutar("  Ciudadana@Example.TEST ", "correcta").rol()).isEqualTo(Rol.CIUDADANO);
    }

    @Test
    void unaClaveIncorrectaEsRechazada() {
        assertThatThrownBy(() -> autenticar.ejecutar("ciudadana@example.test", "otra"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void unEmailDesconocidoFallaIgualQueUnaClaveIncorrectaYTambienVerificaUnHash() {
        // Sin el hash de relleno, la respuesta más rápida delataría qué emails están registrados.
        assertThatThrownBy(() -> autenticar.ejecutar("nadie@example.test", "correcta"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(new CredencialesInvalidasException().getMessage());
        assertThat(hasher.verificaciones).hasSize(1);
    }

    static class HasherFalso implements ClaveHasher {
        final List<String> verificaciones = new ArrayList<>();

        @Override
        public boolean coincide(String clave, String hash) {
            verificaciones.add(hash);
            return ("hash(" + clave + ")").equals(hash);
        }

        @Override
        public String hashDeRelleno() {
            return "hash-de-relleno";
        }
    }
}
