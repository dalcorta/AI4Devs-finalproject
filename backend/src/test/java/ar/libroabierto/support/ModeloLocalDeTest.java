package ar.libroabierto.support;

import ar.libroabierto.privacidad.application.ModeloLocal;
import ar.libroabierto.privacidad.application.ModeloLocalNoDisponibleException;
import ar.libroabierto.privacidad.application.SolicitudModeloLocal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reemplaza a Ollama en los tests con Spring: ningún test levanta un modelo. Lo importa {@code PostgresTestcontainer},
 * así todos los contextos lo tienen y siguen compartiéndose. Por defecto no encuentra nombres; un test puede hacer que
 * encuentre uno o que esté caído, y {@code reiniciar()} lo deja como estaba.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ModeloLocalDeTest {

    @Bean
    @Primary
    ModeloLocalFalso modeloLocalFalso() {
        return new ModeloLocalFalso();
    }

    public static class ModeloLocalFalso implements ModeloLocal {
        private volatile String salida;
        private volatile boolean caido;
        private final AtomicInteger consultas = new AtomicInteger();

        public ModeloLocalFalso() {
            reiniciar();
        }

        @Override
        public String completar(SolicitudModeloLocal solicitud) {
            consultas.incrementAndGet();
            if (caido) {
                throw new ModeloLocalNoDisponibleException("Modelo de prueba caído", null);
            }
            return salida;
        }

        public void encontrar(String... nombres) {
            salida = "{\"nombres\": [" + String.join(", ", java.util.Arrays.stream(nombres)
                    .map(n -> "\"" + n + "\"").toList()) + "]}";
        }

        public void caer() {
            caido = true;
        }

        public int consultas() {
            return consultas.get();
        }

        public void reiniciar() {
            salida = "{\"nombres\": []}";
            caido = false;
            consultas.set(0);
        }
    }
}
