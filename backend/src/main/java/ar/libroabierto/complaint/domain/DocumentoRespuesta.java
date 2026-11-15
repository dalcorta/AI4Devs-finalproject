package ar.libroabierto.complaint.domain;

import ar.libroabierto.shared.hashing.CanonicalHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON público de la respuesta de una empresa, armado campo por campo como {@link DocumentoPublico}. */
public final class DocumentoRespuesta {

    static final String TIPO = "respuesta_empresa";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocumentoRespuesta() {
    }

    /** Devuelve el documento ya en forma canónica: los bytes publicados son exactamente los que se hashean. */
    public static String json(DatosDeRespuesta datos) {
        Map<String, Object> queja = new LinkedHashMap<>();
        queja.put("onchain_id", datos.quejaOnchainId());
        queja.put("content_hash", datos.quejaContentHash());

        Map<String, Object> empresa = new LinkedHashMap<>();
        empresa.put("cuit", datos.empresa().cuit());
        empresa.put("razon_social", datos.empresa().razonSocial());

        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("schema_version", DocumentoPublico.SCHEMA_VERSION);
        documento.put("tipo", TIPO);
        documento.put("queja", queja);
        documento.put("empresa", empresa);
        documento.put("respuesta", Map.of("texto", datos.texto()));
        documento.put("respondida_en", datos.respondidaEn().truncatedTo(ChronoUnit.SECONDS).toString());

        try {
            return CanonicalHasher.canonicalize(MAPPER.writeValueAsString(documento));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta pública", e);
        }
    }
}
