package ar.libroabierto.triage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.triage.application.LlmNoDisponibleException;
import ar.libroabierto.triage.application.RespuestaLlm;
import ar.libroabierto.triage.application.SolicitudLlm;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contra un servidor HTTP local que imita la Messages API: comprueba el pedido exacto sin gastar en el proveedor. */
class AnthropicLlmClientTest {

    private static final String ESQUEMA = """
            {"type": "object", "additionalProperties": false, "required": ["resumen"],
             "properties": {"resumen": {"type": "string", "minLength": 1, "maxLength": 400}}}""";
    private static final SolicitudLlm SOLICITUD =
            new SolicitudLlm("Instrucciones del triage.", "<queja>\n{\"a\":1}\n</queja>", ESQUEMA);

    private HttpServer servidor;
    private final List<Pedido> pedidos = new ArrayList<>();
    private volatile int estado = 200;
    private volatile String respuesta;
    private AnthropicClient cliente;

    record Pedido(String ruta, Map<String, List<String>> cabeceras, JsonNode cuerpo) {
    }

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            byte[] cuerpo = intercambio.getRequestBody().readAllBytes();
            pedidos.add(new Pedido(intercambio.getRequestURI().getPath(), intercambio.getRequestHeaders(),
                    new ObjectMapper().readTree(cuerpo)));
            byte[] salida = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        });
        servidor.start();
        cliente = AnthropicOkHttpClient.builder()
                .apiKey("clave-de-prueba")
                .baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort())
                .maxRetries(0)
                .build();
    }

    @AfterEach
    void apagar() {
        cliente.close();
        servidor.stop(0);
    }

    @Test
    void enviaInstruccionesContenidoYEsquemaConElModeloConfigurado() {
        respuesta = mensaje("end_turn", "{\"resumen\": \"Corte de servicio.\"}");

        RespuestaLlm resultado = new AnthropicLlmClient(cliente, "claude-opus-5-5").completar(SOLICITUD);

        assertThat(resultado).isEqualTo(new RespuestaLlm("{\"resumen\": \"Corte de servicio.\"}", "claude-opus-5-5"));
        assertThat(pedidos).hasSize(1);
        Pedido pedido = pedidos.get(0);
        assertThat(pedido.ruta()).isEqualTo("/v1/messages");
        JsonNode cuerpo = pedido.cuerpo();
        assertThat(cuerpo.get("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(cuerpo.get("system").toString()).contains("Instrucciones del triage.");
        assertThat(cuerpo.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(cuerpo.at("/messages/0").toString()).contains("<queja>");
        assertThat(cuerpo.at("/output_config/format/type").asText()).isEqualTo("json_schema");
        assertThat(cuerpo.at("/output_config/format/schema/required/0").asText()).isEqualTo("resumen");
        assertThat(cuerpo.at("/output_config/format/schema/additionalProperties").asBoolean(true)).isFalse();
    }

    @Test
    void lasRestriccionesQueLaApiNoAceptaQuedanSoloParaLaValidacionLocal() {
        respuesta = mensaje("end_turn", "{}");

        new AnthropicLlmClient(cliente, "claude-opus-5-5").completar(SOLICITUD);

        JsonNode resumen = pedidos.get(0).cuerpo().at("/output_config/format/schema/properties/resumen");
        assertThat(resumen.get("type").asText()).isEqualTo("string");
        assertThat(resumen.has("maxLength")).isFalse();
        assertThat(resumen.has("minLength")).isFalse();
    }

    @Test
    void pideElFallbackDelServidorAnteUnRechazo() {
        respuesta = mensaje("end_turn", "{}");

        new AnthropicLlmClient(cliente, "claude-opus-5-5").completar(SOLICITUD);

        Pedido pedido = pedidos.get(0);
        assertThat(pedido.cuerpo().path("fallbacks").asText()).isEqualTo("default");
        assertThat(String.join(",", pedido.cabeceras().getOrDefault("Anthropic-beta", List.of())))
                .contains("server-side-fallback-2026-07-01");
    }

    @Test
    void unRechazoDelModeloDevuelveTextoVacioQueLuegoSeraInvalido() {
        respuesta = mensaje("refusal", null);

        assertThat(new AnthropicLlmClient(cliente, "claude-opus-5-5").completar(SOLICITUD).texto()).isEmpty();
    }

    @Test
    void unErrorDelProveedorEsLlmNoDisponible() {
        estado = 529;
        respuesta = "{\"type\": \"error\", \"error\": {\"type\": \"overloaded_error\", \"message\": \"Overloaded\"}}";

        assertThatThrownBy(() -> new AnthropicLlmClient(cliente, "claude-opus-5-5").completar(SOLICITUD))
                .isInstanceOf(LlmNoDisponibleException.class);
    }

    private static String mensaje(String stopReason, String texto) {
        String contenido = texto == null ? "[]"
                : "[{\"type\": \"text\", \"text\": " + new ObjectMapper().valueToTree(texto) + "}]";
        return """
                {"id": "msg_prueba", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                 "content": %s, "stop_reason": "%s", "stop_sequence": null,
                 "usage": {"input_tokens": 10, "output_tokens": 5}}""".formatted(contenido, stopReason);
    }
}
