package ar.libroabierto.complaint.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.DocumentoPublico;
import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, JpaQuejaRepository.class})
class JpaQuejaRepositoryTest {

    private static final String HASH = "0x" + "b".repeat(64);
    private static final String TX = "0x" + "c".repeat(64);

    @Autowired
    private JpaQuejaRepository repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager em;

    private UUID usuario;
    private UUID empresa;

    @BeforeEach
    void datosBase() {
        usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, dni_cifrado)
                VALUES (?, 'denunciante@example.test', 'x', 'CIUDADANO', 'Persona Ficticia', '\\x01'::bytea)""",
                usuario);
        empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, '30712345678', 'Empresa Ficticia S.A.')",
                empresa);
    }

    @Test
    void pendientesDeAnclajeDevuelveSoloPendientesConEmpresaYEvidenciasEnOrden() {
        UUID pendiente = queja("PENDIENTE", new BigDecimal("15000"), "2026-10-18T12:00:00Z");
        queja("ENVIADA", null, "2026-10-18T13:00:00Z");
        evidencia(pendiente, "bafyexample2", "2026-10-18T12:00:02Z");
        evidencia(pendiente, "bafyexample1", "2026-10-18T12:00:01Z");

        List<Queja> pendientes = repositorio.pendientesDeAnclaje();

        assertThat(pendientes).hasSize(1);
        Queja queja = pendientes.get(0);
        assertThat(queja.id()).isEqualTo(pendiente);
        assertThat(queja.usuarioId()).isEqualTo(usuario);
        assertThat(queja.empresa().cuit()).isEqualTo("30712345678");
        assertThat(queja.empresa().razonSocial()).isEqualTo("Empresa Ficticia S.A.");
        assertThat(queja.evidenciasCids()).containsExactly("bafyexample1", "bafyexample2");
        assertThat(queja.registradaEn()).isEqualTo(Instant.parse("2026-10-18T12:00:00Z"));
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.PENDIENTE);
    }

    @Test
    void elDocumentoReconstruidoDesdeLaBaseCoincideConElVectorCompartido() {
        // Mismos datos que el ejemplo del readme: numeric(14,2) devuelve 15000.00 y JCS lo normaliza a 15000.
        UUID id = queja("PENDIENTE", new BigDecimal("15000"), "2026-10-18T12:00:00Z");
        evidencia(id, "bafyexample1", "2026-10-18T12:00:01Z");
        evidencia(id, "bafyexample2", "2026-10-18T12:00:02Z");

        Queja queja = repositorio.pendientesDeAnclaje().get(0);

        assertThat(CanonicalHasher.hash(DocumentoPublico.json(queja)))
                .isEqualTo("0x6a6fdd46debb996c2eacc63503f54a9dc372ae83424c8edced8e64a139962d4b");
    }

    @Test
    void guardarPersisteLaPublicacionSinCambiarElEstado() {
        queja("PENDIENTE", null, "2026-10-18T12:00:00Z");
        Queja queja = repositorio.pendientesDeAnclaje().get(0);
        queja.registrarPublicacion("bafy-doc", HASH);

        repositorio.guardar(queja);
        sincronizar();

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT ipfs_cid, content_hash, anchor_status FROM queja WHERE id = ?", queja.id());
        assertThat(fila).containsEntry("ipfs_cid", "bafy-doc")
                .containsEntry("content_hash", HASH)
                .containsEntry("anchor_status", "PENDIENTE");
        assertThat(repositorio.pendientesDeAnclaje().get(0).contentHash()).isEqualTo(HASH);
    }

    @Test
    void guardarUnaQuejaEnviadaRegistraLaTransaccionUnaSolaVez() {
        queja("PENDIENTE", null, "2026-10-18T12:00:00Z");
        Queja queja = repositorio.pendientesDeAnclaje().get(0);
        queja.registrarPublicacion("bafy-doc", HASH);
        queja.marcarEnviada(TX);

        repositorio.guardar(queja);
        repositorio.guardar(queja);
        sincronizar();

        assertThat(jdbc.queryForObject("SELECT anchor_status FROM queja WHERE id = ?", String.class, queja.id()))
                .isEqualTo("ENVIADA");
        List<Map<String, Object>> transacciones = jdbc.queryForList(
                "SELECT tipo, referencia_id, estado FROM transaccion_blockchain WHERE tx_hash = ?", TX);
        assertThat(transacciones).hasSize(1);
        assertThat(transacciones.get(0)).containsEntry("tipo", "REGISTRO")
                .containsEntry("referencia_id", queja.id())
                .containsEntry("estado", "ENVIADA");
        assertThat(repositorio.pendientesDeAnclaje()).isEmpty();
    }

    private void sincronizar() {
        em.flush();
        em.clear();
    }

    private UUID queja(String anchorStatus, BigDecimal monto, String creadaEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, monto_reclamado,
                                   fecha_hecho, anchor_status, created_at)
                VALUES (?, ?, ?, 'telecomunicaciones',
                        'Corte de servicio de internet durante 15 días sin respuesta del soporte.',
                        ?, DATE '2026-10-15', ?, ?::timestamptz)""",
                id, usuario, empresa, monto, anchorStatus, creadaEn);
        return id;
    }

    private void evidencia(UUID queja, String cid, String creadaEn) {
        jdbc.update("""
                INSERT INTO evidencia (id, queja_id, ipfs_cid, mime_type, created_at)
                VALUES (?, ?, ?, 'image/jpeg', ?::timestamptz)""", UUID.randomUUID(), queja, cid, creadaEn);
    }
}
