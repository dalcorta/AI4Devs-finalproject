package ar.libroabierto.complaint.infrastructure;

import ar.libroabierto.complaint.application.IpfsGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Publica en un nodo Kubo por su API HTTP. El contenido queda pineado y se direcciona con CID v1. */
public class KuboIpfsGateway implements IpfsGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI addEndpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public KuboIpfsGateway(String apiUrl) {
        this.addEndpoint = URI.create(apiUrl + "/api/v0/add?cid-version=1&pin=true");
    }

    @Override
    public String publicar(String json) {
        String boundary = "----libroabierto-" + UUID.randomUUID();
        HttpRequest pedido = HttpRequest.newBuilder(addEndpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, json)))
                .build();
        try {
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new IllegalStateException("Kubo respondió " + respuesta.statusCode() + ": " + respuesta.body());
            }
            JsonNode cuerpo = MAPPER.readTree(respuesta.body());
            return cuerpo.get("Hash").asText();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo publicar en IPFS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación en IPFS interrumpida", e);
        }
    }

    private static byte[] multipart(String boundary, String json) {
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        cuerpo.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"queja.json\"\r\n"
                + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(json.getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return cuerpo.toByteArray();
    }
}
