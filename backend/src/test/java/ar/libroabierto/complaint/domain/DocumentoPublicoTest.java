package ar.libroabierto.complaint.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.shared.hashing.CanonicalHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class DocumentoPublicoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID USUARIO_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void ejemploDelReadmeProduceElHashDelVectorCompartido() throws IOException {
        Queja queja = quejaDeEjemplo(new BigDecimal("15000"));

        String hash = CanonicalHasher.hash(DocumentoPublico.json(queja));

        assertThat(hash).isEqualTo(vectorCompartido("queja_publica_ejemplo"));
    }

    @Test
    void noIncluyeDatosDelDenunciante() throws IOException {
        String json = DocumentoPublico.json(quejaDeEjemplo(new BigDecimal("15000")));
        JsonNode doc = MAPPER.readTree(json);

        assertThat(campos(doc))
                .containsExactlyInAnyOrder("schema_version", "empresa", "queja", "evidencias_cids", "registrada_en");
        assertThat(campos(doc.get("queja")))
                .containsExactlyInAnyOrder("categoria", "descripcion", "monto_reclamado_ars", "fecha_hecho");
        assertThat(campos(doc.get("empresa"))).containsExactlyInAnyOrder("cuit", "razon_social");
        assertThat(json).doesNotContain(USUARIO_ID.toString());
    }

    @Test
    void omiteElMontoCuandoNoSeReclamaDinero() throws IOException {
        JsonNode doc = MAPPER.readTree(DocumentoPublico.json(quejaDeEjemplo(null)));

        assertThat(campos(doc.get("queja"))).doesNotContain("monto_reclamado_ars");
    }

    @Test
    void elDocumentoYaEstaEnFormaCanonica() {
        String json = DocumentoPublico.json(quejaDeEjemplo(new BigDecimal("15000")));

        assertThat(json).isEqualTo(CanonicalHasher.canonicalize(json));
    }

    static Queja quejaDeEjemplo(BigDecimal monto) {
        return Queja.nueva(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                USUARIO_ID,
                new Empresa("30712345678", "Empresa Ficticia S.A."),
                "telecomunicaciones",
                "Corte de servicio de internet durante 15 días sin respuesta del soporte.",
                monto,
                LocalDate.parse("2026-10-15"),
                List.of("bafyexample1", "bafyexample2"),
                Instant.parse("2026-10-18T12:00:00.123456Z"));
    }

    private static Set<String> campos(JsonNode node) {
        Set<String> nombres = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    private static String vectorCompartido(String nombre) throws IOException {
        JsonNode vectors = MAPPER.readTree(Path.of("..", "shared", "test-vectors", "canonical-hash.json").toFile())
                .get("vectors");
        return StreamSupport.stream(vectors.spliterator(), false)
                .filter(v -> v.get("name").asText().equals(nombre))
                .findFirst()
                .orElseThrow()
                .get("keccak256")
                .asText();
    }
}
