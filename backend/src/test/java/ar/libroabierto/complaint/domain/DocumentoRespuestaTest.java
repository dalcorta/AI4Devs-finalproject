package ar.libroabierto.complaint.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.shared.hashing.CanonicalHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class DocumentoRespuestaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void elEjemploProduceElHashDelVectorCompartido() throws IOException {
        assertThat(CanonicalHasher.hash(DocumentoRespuesta.json(ejemplo()))).isEqualTo(vector("respuesta_empresa_ejemplo"));
    }

    @Test
    void soloLlevaLaQuejaALaQueRespondeLaEmpresaYElTexto() throws IOException {
        JsonNode doc = MAPPER.readTree(DocumentoRespuesta.json(ejemplo()));

        assertThat(campos(doc)).containsExactlyInAnyOrder(
                "schema_version", "tipo", "queja", "empresa", "respuesta", "respondida_en");
        assertThat(doc.get("tipo").asText()).isEqualTo("respuesta_empresa");
        // La queja se referencia por lo que es público y está on-chain: id y hash, nunca el UUID interno.
        assertThat(campos(doc.get("queja"))).containsExactlyInAnyOrder("onchain_id", "content_hash");
        assertThat(campos(doc.get("empresa"))).containsExactlyInAnyOrder("cuit", "razon_social");
        assertThat(campos(doc.get("respuesta"))).containsExactlyInAnyOrder("texto");
    }

    @Test
    void elInstanteSeSerializaSinFraccionesDeSegundo() throws IOException {
        DatosDeRespuesta conFraccion = new DatosDeRespuesta(1, ejemplo().quejaContentHash(), ejemplo().empresa(),
                ejemplo().texto(), Instant.parse("2026-10-25T15:30:00.987654Z"));

        assertThat(MAPPER.readTree(DocumentoRespuesta.json(conFraccion)).get("respondida_en").asText())
                .isEqualTo("2026-10-25T15:30:00Z");
    }

    @Test
    void elDocumentoYaEstaEnFormaCanonica() {
        String json = DocumentoRespuesta.json(ejemplo());

        assertThat(json).isEqualTo(CanonicalHasher.canonicalize(json));
    }

    static DatosDeRespuesta ejemplo() {
        return new DatosDeRespuesta(1, "0x6a6fdd46debb996c2eacc63503f54a9dc372ae83424c8edced8e64a139962d4b",
                new Empresa("30712345678", "Empresa Ficticia S.A."),
                "Restablecimos el servicio el 20/10 y acreditamos una bonificación por los días sin conexión.",
                Instant.parse("2026-10-25T15:30:00Z"));
    }

    private static Set<String> campos(JsonNode node) {
        Set<String> nombres = new HashSet<>();
        node.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    private static String vector(String nombre) throws IOException {
        JsonNode vectors = MAPPER.readTree(Path.of("..", "shared", "test-vectors", "canonical-hash.json").toFile())
                .get("vectors");
        return StreamSupport.stream(vectors.spliterator(), false)
                .filter(v -> v.get("name").asText().equals(nombre))
                .findFirst().orElseThrow().get("keccak256").asText();
    }
}
