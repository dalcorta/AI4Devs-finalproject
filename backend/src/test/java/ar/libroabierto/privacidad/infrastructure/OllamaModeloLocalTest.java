package ar.libroabierto.privacidad.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.privacidad.application.ModeloLocalNoDisponibleException;
import ar.libroabierto.privacidad.application.SolicitudModeloLocal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contra un servidor HTTP local que imita la API de chat de Ollama: comprueba el pedido sin levantar un modelo. */
class OllamaModeloLocalTest {

    private static final String ESQUEMA = """
            {"type": "object", "required": ["nombres"], "properties": {"nombres": {"type": "array"}}}""";
    private static final SolicitudModeloLocal SOLICITUD =
            new SolicitudModeloLocal("Instrucciones.", "<texto>\nMe atendió Juan.\n</texto>", ESQUEMA);

    private HttpServer servidor;
    private final List<JsonNode> pedidos = new ArrayList<>();
    private final List<String> rutas = new ArrayList<>();
    private volatile int estado = 200;
    private volatile String respuesta;
    private volatile long demora;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
            pedidos.add(new ObjectMapper().readTree(intercambio.getRequestBody().readAllBytes()));
            try {
                Thread.sleep(demora);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] salida = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    @Test
    void enviaUnChatSinStreamingConElEsquemaComoFormatoYTemperaturaCero() throws IOException {
        respuesta = chat("{\"nombres\": [\"Juan\"]}");

        String salida = modelo(Duration.ofSeconds(5)).completar(SOLICITUD);

        assertThat(salida).isEqualTo("{\"nombres\": [\"Juan\"]}");
        assertThat(rutas).containsExactly("POST /api/chat");
        JsonNode pedido = pedidos.get(0);
        assertThat(pedido.get("model").asText()).isEqualTo("qwen2.5:1.5b");
        assertThat(pedido.get("stream").asBoolean()).isFalse();
        assertThat(pedido.get("format")).isEqualTo(new ObjectMapper().readTree(ESQUEMA));
        assertThat(pedido.at("/options/temperature").asDouble()).isZero();
        assertThat(pedido.get("messages")).hasSize(2);
        assertThat(pedido.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(pedido.at("/messages/0/content").asText()).isEqualTo("Instrucciones.");
        assertThat(pedido.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(pedido.at("/messages/1/content").asText()).isEqualTo("<texto>\nMe atendió Juan.\n</texto>");
    }

    @Test
    void unErrorDelServidorEsModeloNoDisponibleSinIncluirElCuerpo() {
        estado = 500;
        respuesta = "{\"error\": \"model 'qwen2.5:1.5b' not found, texto: Me atendió Juan\"}";

        assertThatThrownBy(() -> modelo(Duration.ofSeconds(5)).completar(SOLICITUD))
                .isInstanceOf(ModeloLocalNoDisponibleException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining("Juan");
    }

    @Test
    void unaRespuestaSinContenidoEsModeloNoDisponible() {
        respuesta = "{\"message\": {\"role\": \"assistant\"}}";

        assertThatThrownBy(() -> modelo(Duration.ofSeconds(5)).completar(SOLICITUD))
                .isInstanceOf(ModeloLocalNoDisponibleException.class);
    }

    @Test
    void siTardaMasQueElTiempoLimiteEsModeloNoDisponible() {
        respuesta = chat("{\"nombres\": []}");
        demora = 1500;

        assertThatThrownBy(() -> modelo(Duration.ofMillis(300)).completar(SOLICITUD))
                .isInstanceOf(ModeloLocalNoDisponibleException.class);
    }

    @Test
    void siNoHayNadieEscuchandoEsModeloNoDisponible() throws IOException {
        int puertoLibre;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoLibre = socket.getLocalPort();
        }

        assertThatThrownBy(() -> new OllamaModeloLocal("http://127.0.0.1:" + puertoLibre, "qwen2.5:1.5b",
                Duration.ofSeconds(2)).completar(SOLICITUD))
                .isInstanceOf(ModeloLocalNoDisponibleException.class);
    }

    private OllamaModeloLocal modelo(Duration limite) {
        return new OllamaModeloLocal("http://127.0.0.1:" + servidor.getAddress().getPort() + "/", "qwen2.5:1.5b",
                limite);
    }

    private static String chat(String contenido) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "model", "qwen2.5:1.5b", "done", true,
                    "message", java.util.Map.of("role", "assistant", "content", contenido)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
