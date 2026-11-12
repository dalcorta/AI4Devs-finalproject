package ar.libroabierto.triage.application;

import ar.libroabierto.triage.domain.ArticulosLey24240;
import ar.libroabierto.triage.domain.Prioridad;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La salida del LLM es un dato no confiable: se parsea, se valida contra el JSON Schema del triage y los artículos se
 * filtran contra la lista cerrada de la Ley 24.240. El esquema también se le envía al proveedor, pero esta validación
 * no depende de que el proveedor lo haya respetado.
 */
public class ValidadorSalidaTriage {

    static final String ESQUEMA = PromptTriage.leerRecurso("/triage/salida-triage.schema.json");
    private static final int MAX_LINEAS_RESUMEN = 3;

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema esquema =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(ESQUEMA);

    public SalidaTriage validar(String texto) {
        JsonNode salida = parsear(texto);
        Set<ValidationMessage> errores = esquema.validate(salida);
        if (!errores.isEmpty()) {
            throw new SalidaInvalidaException("No cumple el esquema: "
                    + errores.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.joining("; ")));
        }

        String resumen = salida.get("resumen").asText().strip();
        if (resumen.lines().count() > MAX_LINEAS_RESUMEN) {
            throw new SalidaInvalidaException("El resumen tiene más de " + MAX_LINEAS_RESUMEN + " líneas");
        }

        Set<String> citados = new LinkedHashSet<>();
        salida.get("articulos").forEach(articulo -> citados.add(articulo.asText().strip()));
        List<String> permitidos = new ArrayList<>();
        List<String> descartados = new ArrayList<>();
        for (String articulo : citados) {
            (ArticulosLey24240.permitido(articulo) ? permitidos : descartados).add(articulo);
        }

        return new SalidaTriage(salida.get("categoria").asText().strip(), resumen,
                Prioridad.valueOf(salida.get("prioridad").asText()), permitidos, descartados);
    }

    private JsonNode parsear(String texto) {
        try {
            JsonNode nodo = texto == null || texto.isBlank() ? null : mapper.readTree(texto);
            if (nodo == null || !nodo.isObject()) {
                throw new SalidaInvalidaException("La salida no es un objeto JSON");
            }
            return nodo;
        } catch (JsonProcessingException e) {
            throw new SalidaInvalidaException("La salida no es JSON válido");
        }
    }
}
