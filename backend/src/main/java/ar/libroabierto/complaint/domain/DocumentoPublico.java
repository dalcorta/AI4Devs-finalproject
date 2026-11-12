package ar.libroabierto.complaint.domain;

import ar.libroabierto.shared.hashing.CanonicalHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON que se publica en IPFS y cuyo hash se ancla on-chain. Se arma campo por campo a partir de una lista
 * explícita: ningún dato del denunciante (ni siquiera su id interno) puede terminar acá por accidente.
 */
public final class DocumentoPublico {

    static final String SCHEMA_VERSION = "1.0";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocumentoPublico() {
    }

    /** Devuelve el documento ya en forma canónica, así los bytes publicados son exactamente los que se hashean. */
    public static String json(Queja queja) {
        Map<String, Object> empresa = new LinkedHashMap<>();
        empresa.put("cuit", queja.empresa().cuit());
        empresa.put("razon_social", queja.empresa().razonSocial());

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("categoria", queja.categoria());
        contenido.put("descripcion", queja.descripcion());
        if (queja.montoReclamado() != null) {
            contenido.put("monto_reclamado_ars", queja.montoReclamado());
        }
        contenido.put("fecha_hecho", queja.fechaHecho().toString());

        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("schema_version", SCHEMA_VERSION);
        documento.put("empresa", empresa);
        documento.put("queja", contenido);
        documento.put("evidencias_cids", queja.evidenciasCids());
        // Sin fracciones de segundo: el instante debe serializarse igual en cualquier reintento.
        documento.put("registrada_en", queja.registradaEn().truncatedTo(ChronoUnit.SECONDS).toString());

        try {
            return CanonicalHasher.canonicalize(MAPPER.writeValueAsString(documento));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el documento público", e);
        }
    }
}
