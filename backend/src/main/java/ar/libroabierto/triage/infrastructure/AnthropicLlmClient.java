package ar.libroabierto.triage.infrastructure;

import ar.libroabierto.triage.application.LlmClient;
import ar.libroabierto.triage.application.LlmNoDisponibleException;
import ar.libroabierto.triage.application.RespuestaLlm;
import ar.libroabierto.triage.application.SolicitudLlm;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptador de {@link LlmClient} sobre la Messages API de Anthropic, con salida estructurada.
 *
 * <p>Pide el fallback del servidor ({@code fallbacks: "default"}): si el modelo rechaza el pedido, la API lo reintenta
 * con otro modelo según la categoría del rechazo. Si igual termina en rechazo, devuelve texto vacío, que la validación
 * trata como salida inválida.
 */
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    // Restricciones que la salida estructurada de la API no admite. Siguen valiendo: las controla la validación local.
    private static final Set<String> SOLO_VALIDACION_LOCAL =
            Set.of("$schema", "minLength", "maxLength", "pattern", "minItems", "maxItems");
    private static final long MAX_TOKENS = 16_000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicClient cliente;
    private final String modelo;

    public AnthropicLlmClient(AnthropicClient cliente, String modelo) {
        this.cliente = cliente;
        this.modelo = modelo;
    }

    @Override
    public RespuestaLlm completar(SolicitudLlm solicitud) {
        MessageCreateParams pedido = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAX_TOKENS)
                .system(solicitud.instrucciones())
                .addUserMessage(solicitud.contenido())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.MEDIUM)
                        .format(BetaJsonOutputFormat.builder()
                                .schema(esquemaParaApi(solicitud.esquemaJson()))
                                .build())
                        .build())
                .fallbacks(BetaFallbacksParam.ofDefault())
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .build();

        BetaMessage respuesta;
        try {
            respuesta = cliente.beta().messages().create(pedido);
        } catch (BadRequestException | UnauthorizedException | PermissionDeniedException | NotFoundException e) {
            // Clave, modelo o pedido mal configurados: reintentar no lo arregla y no es culpa de la queja.
            throw new IllegalStateException("El proveedor rechazó el pedido de triage por configuración", e);
        } catch (AnthropicException e) {
            throw new LlmNoDisponibleException("El proveedor del LLM no respondió", e);
        }

        if (respuesta.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            log.warn("El modelo rechazó el pedido de triage incluso con fallback");
            return new RespuestaLlm("", respuesta.model().asString());
        }
        String texto = respuesta.content().stream()
                .flatMap(bloque -> bloque.text().stream())
                .map(bloque -> bloque.text())
                .collect(Collectors.joining());
        return new RespuestaLlm(texto, respuesta.model().asString());
    }

    private BetaJsonOutputFormat.Schema esquemaParaApi(String esquemaJson) {
        try {
            JsonNode esquema = mapper.readTree(esquemaJson);
            quitarRestricciones(esquema);
            Map<String, JsonValue> propiedades = new LinkedHashMap<>();
            esquema.fields().forEachRemaining(campo ->
                    propiedades.put(campo.getKey(), JsonValue.from(mapper.convertValue(campo.getValue(), Object.class))));
            return BetaJsonOutputFormat.Schema.builder().putAllAdditionalProperties(propiedades).build();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El esquema de salida no es JSON válido", e);
        }
    }

    /** Recorre un (sub)esquema; dentro de "properties" las claves son nombres de campo, no palabras clave. */
    private static void quitarRestricciones(JsonNode esquema) {
        if (!(esquema instanceof ObjectNode objeto)) {
            return;
        }
        objeto.remove(SOLO_VALIDACION_LOCAL);
        JsonNode propiedades = objeto.get("properties");
        if (propiedades != null) {
            propiedades.forEach(AnthropicLlmClient::quitarRestricciones);
        }
        JsonNode items = objeto.get("items");
        if (items != null) {
            quitarRestricciones(items);
        }
    }
}
