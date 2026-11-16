package ar.libroabierto.config;

import ar.libroabierto.privacidad.application.ModeloLocal;
import ar.libroabierto.privacidad.application.ModeloLocalNoDisponibleException;
import ar.libroabierto.privacidad.application.RevisarDatosPersonales;
import ar.libroabierto.privacidad.infrastructure.OllamaModeloLocal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HU-06. La detección lee texto todavía privado (regla 2 de CLAUDE.md): si la URL del modelo apunta fuera de la máquina
 * o de la red privada, la aplicación no arranca, así nadie la manda por error a un proveedor externo.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LibroAbiertoProperties.class)
public class PrivacidadConfig {

    @Bean
    ModeloLocal modeloLocal(LibroAbiertoProperties props) {
        LibroAbiertoProperties.Privacidad privacidad = props.privacidad();
        if (!privacidad.llmHabilitado()) {
            return solicitud -> {
                throw new ModeloLocalNoDisponibleException("El modelo local está deshabilitado (PII_LLM_HABILITADO)",
                        null);
            };
        }
        if (!esLocal(privacidad.ollamaUrl())) {
            throw new IllegalStateException("PII_OLLAMA_URL tiene que apuntar a un Ollama en la máquina o en la red "
                    + "privada: la detección de datos personales lee texto que todavía no es público");
        }
        return new OllamaModeloLocal(privacidad.ollamaUrl(), privacidad.modelo(), privacidad.timeout());
    }

    @Bean
    RevisarDatosPersonales revisarDatosPersonales(ModeloLocal modelo) {
        return new RevisarDatosPersonales(modelo);
    }

    /**
     * Local es {@code localhost}, una IP de loopback o de una red privada, o un nombre sin puntos, como el de un servicio
     * de compose ({@code ollama}). Un nombre con dominio no se resuelve: podría apuntar a cualquier lado.
     */
    static boolean esLocal(String url) {
        String host;
        try {
            URI uri = URI.create(url);
            host = uri.getHost();
            if (host == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        boolean literal = host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        if (!literal) {
            return !host.contains(".");
        }
        try {
            // Con una IP literal, getByName no consulta DNS.
            InetAddress ip = InetAddress.getByName(host);
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
