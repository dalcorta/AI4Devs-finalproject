package ar.libroabierto.privacidad.infrastructure;

import ar.libroabierto.privacidad.application.ModeloLocal;
import ar.libroabierto.privacidad.application.ModeloLocalNoDisponibleException;
import ar.libroabierto.privacidad.application.SolicitudModeloLocal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Adaptador de {@link ModeloLocal} sobre la API de chat de Ollama ({@code POST /api/chat}), sin streaming y con el JSON
 * Schema como {@code format}, para que el modelo responda un objeto con esa forma. Ningún error incluye el cuerpo del
 * pedido ni el de la respuesta: los dos pueden tener el texto revisado.
 */
public class OllamaModeloLocal implements ModeloLocal {

    private final URI chat;
    private final String modelo;
    private final Duration limite;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaModeloLocal(String url, String modelo, Duration limite) {
        this.chat = URI.create(url.replaceAll("/+$", "") + "/api/chat");
        this.modelo = modelo;
        this.limite = limite;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(limite).build();
    }

    @Override
    public String completar(SolicitudModeloLocal solicitud) {
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(HttpRequest.newBuilder(chat)
                            .timeout(limite)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo(solicitud), StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ModeloLocalNoDisponibleException("Ollama no respondió (" + e.getClass().getSimpleName() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModeloLocalNoDisponibleException("Se interrumpió la espera a Ollama", e);
        }
        if (respuesta.statusCode() != 200) {
            throw new ModeloLocalNoDisponibleException("Ollama respondió " + respuesta.statusCode(), null);
        }
        try {
            JsonNode contenido = mapper.readTree(respuesta.body()).at("/message/content");
            if (!contenido.isTextual()) {
                throw new ModeloLocalNoDisponibleException("La respuesta de Ollama no trae contenido", null);
            }
            return contenido.asText();
        } catch (IOException e) {
            throw new ModeloLocalNoDisponibleException("La respuesta de Ollama no es JSON", null);
        }
    }

    private String cuerpo(SolicitudModeloLocal solicitud) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode()
                    .put("model", modelo)
                    .put("stream", false);
            cuerpo.set("format", mapper.readTree(solicitud.esquema()));
            cuerpo.putObject("options").put("temperature", 0);
            cuerpo.putArray("messages")
                    .add(mapper.createObjectNode().put("role", "system").put("content", solicitud.instrucciones()))
                    .add(mapper.createObjectNode().put("role", "user").put("content", solicitud.contenido()));
            return mapper.writeValueAsString(cuerpo);
        } catch (IOException e) {
            throw new IllegalStateException("El esquema de la salida no es JSON válido", e);
        }
    }
}
