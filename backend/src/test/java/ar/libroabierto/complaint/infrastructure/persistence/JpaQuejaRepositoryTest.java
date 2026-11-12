package ar.libroabierto.complaint.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.DocumentoPublico;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    @Test
    void porContentHashEncuentraLaQuejaConSuEmpresaYSuIdOnChain() {
        UUID id = queja("PENDIENTE", null, "2026-10-18T12:00:00Z");
        queja("PENDIENTE", null, "2026-10-18T13:00:00Z");
        jdbc.update("UPDATE queja SET content_hash = ?, ipfs_cid = 'bafy-doc', anchor_status = 'CONFIRMADA', "
                + "onchain_id = 3 WHERE id = ?", HASH, id);

        Queja queja = repositorio.porContentHash(HASH).orElseThrow();

        assertThat(queja.id()).isEqualTo(id);
        assertThat(queja.empresa().cuit()).isEqualTo("30712345678");
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.CONFIRMADA);
        assertThat(queja.onchainId()).isEqualTo(3L);
        assertThat(repositorio.porContentHash("0x" + "d".repeat(64))).isEmpty();
    }

    @Test
    void guardarUnaQuejaConfirmadaSeteaOnchainIdYConfirmaSuTransaccion() {
        queja("PENDIENTE", null, "2026-10-18T12:00:00Z");
        Queja queja = repositorio.pendientesDeAnclaje().get(0);
        queja.registrarPublicacion("bafy-doc", HASH);
        queja.marcarEnviada(TX);
        repositorio.guardar(queja);
        sincronizar();

        Queja recuperada = repositorio.porContentHash(HASH).orElseThrow();
        recuperada.confirmarAnclaje(5, TX, 1234);
        repositorio.guardar(recuperada);
        sincronizar();

        assertThat(jdbc.queryForMap("SELECT anchor_status, onchain_id FROM queja WHERE id = ?", queja.id()))
                .containsEntry("anchor_status", "CONFIRMADA")
                .containsEntry("onchain_id", 5L);
        List<Map<String, Object>> transacciones = jdbc.queryForList(
                "SELECT estado, block_number FROM transaccion_blockchain WHERE tx_hash = ?", TX);
        assertThat(transacciones).hasSize(1);
        assertThat(transacciones.get(0)).containsEntry("estado", "CONFIRMADA").containsEntry("block_number", 1234L);
    }

    @Test
    void siLaTransaccionNoHabiaQuedadoRegistradaLaConfirmacionLaCrea() {
        // El worker se cayó después de enviar la transacción y antes de guardarla: la queja sigue PENDIENTE.
        queja("PENDIENTE", null, "2026-10-18T12:00:00Z");
        Queja queja = repositorio.pendientesDeAnclaje().get(0);
        queja.registrarPublicacion("bafy-doc", HASH);
        repositorio.guardar(queja);
        sincronizar();

        Queja recuperada = repositorio.porContentHash(HASH).orElseThrow();
        recuperada.confirmarAnclaje(5, TX, 1234);
        repositorio.guardar(recuperada);
        sincronizar();

        assertThat(jdbc.queryForMap(
                "SELECT tipo, referencia_id, estado, block_number FROM transaccion_blockchain WHERE tx_hash = ?", TX))
                .containsEntry("tipo", "REGISTRO")
                .containsEntry("referencia_id", queja.id())
                .containsEntry("estado", "CONFIRMADA")
                .containsEntry("block_number", 1234L);
        assertThat(repositorio.pendientesDeAnclaje()).isEmpty();
    }

    @Test
    void confirmadasSinTriageDevuelveSoloLasConfirmadasQueTodaviaNoTienenTriage() {
        UUID sinTriage = confirmada("2026-10-18T12:00:00Z", 1);
        UUID conTriage = confirmada("2026-10-18T11:00:00Z", 2);
        queja("PENDIENTE", null, "2026-10-18T10:00:00Z");
        queja("ENVIADA", null, "2026-10-18T09:00:00Z");
        jdbc.update("""
                INSERT INTO triage (id, queja_id, estado, modelo, intentos)
                VALUES (?, ?, 'MANUAL', 'modelo', 3)""", UUID.randomUUID(), conTriage);
        UUID otraSinTriage = confirmada("2026-10-18T13:00:00Z", 3);

        List<Queja> pendientes = repositorio.confirmadasSinTriage(10);

        assertThat(pendientes).extracting(Queja::id).containsExactly(sinTriage, otraSinTriage);
        assertThat(pendientes.get(0).empresa().cuit()).isEqualTo("30712345678");
        assertThat(repositorio.confirmadasSinTriage(1)).extracting(Queja::id).containsExactly(sinTriage);
    }

    @Test
    void registrarInsertaLaQuejaPendienteYCreaLaEmpresaSiNoExiste() {
        Queja nueva = Queja.nueva(UUID.randomUUID(), usuario, new Empresa("30712345671", "Empresa Nueva S.A."),
                "bancos", "Cobro indebido.", new BigDecimal("100.50"), LocalDate.parse("2026-11-01"), List.of(),
                Instant.parse("2026-11-12T15:00:00Z"));

        repositorio.registrar(nueva);
        sincronizar();

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT q.anchor_status, q.created_at, e.cuit, e.razon_social
                FROM queja q JOIN empresa e ON e.id = q.empresa_id WHERE q.id = ?""", nueva.id());
        assertThat(fila).containsEntry("anchor_status", "PENDIENTE")
                .containsEntry("cuit", "30712345671")
                .containsEntry("razon_social", "Empresa Nueva S.A.");
        assertThat(((java.sql.Timestamp) fila.get("created_at")).toInstant())
                .isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
        assertThat(repositorio.pendientesDeAnclaje()).extracting(Queja::id).containsExactly(nueva.id());
    }

    @Test
    void registrarConUnCuitExistenteReusaLaEmpresa() {
        Queja nueva = Queja.nueva(UUID.randomUUID(), usuario, new Empresa("30712345678", "Empresa Ficticia S.A."),
                "bancos", "Cobro indebido.", null, LocalDate.parse("2026-11-01"), List.of(),
                Instant.parse("2026-11-12T15:00:00Z"));

        repositorio.registrar(nueva);
        sincronizar();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM empresa", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM queja WHERE id = ?", UUID.class, nueva.id()))
                .isEqualTo(empresa);
    }

    @Test
    void porCuitDevuelveLaEmpresaRegistrada() {
        assertThat(repositorio.porCuit("30712345678")).contains(new Empresa("30712345678", "Empresa Ficticia S.A."));
        assertThat(repositorio.porCuit("30712345671")).isEmpty();
    }

    @Test
    void registradasDesdeCuentaSoloLasDelUsuarioDesdeEseInstante() {
        queja("PENDIENTE", null, "2026-11-12T02:59:59Z");
        queja("PENDIENTE", null, "2026-11-12T03:00:00Z");
        queja("ENVIADA", null, "2026-11-12T20:00:00Z");

        assertThat(repositorio.registradasDesde(usuario, Instant.parse("2026-11-12T03:00:00Z"))).isEqualTo(2);
        assertThat(repositorio.registradasDesde(UUID.randomUUID(), Instant.parse("2026-11-12T03:00:00Z"))).isZero();
    }

    private UUID confirmada(String creadaEn, long onchainId) {
        UUID id = queja("PENDIENTE", null, creadaEn);
        jdbc.update("UPDATE queja SET content_hash = ?, ipfs_cid = 'bafy-doc', anchor_status = 'CONFIRMADA', "
                + "onchain_id = ? WHERE id = ?", String.format("0x%064x", onchainId), onchainId, id);
        return id;
    }

    private void sincronizar() {
        em.flush();
        em.clear();
    }

    private UUID queja(String anchorStatus, BigDecimal monto, String creadaEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, monto_reclamado,
                                   fecha_hecho, anchor_status, content_hash, created_at)
                VALUES (?, ?, ?, 'telecomunicaciones',
                        'Corte de servicio de internet durante 15 días sin respuesta del soporte.',
                        ?, DATE '2026-10-15', ?, ?, ?::timestamptz)""",
                id, usuario, empresa, monto, anchorStatus,
                // Una queja ya enviada tiene hash (lo exige el esquema); uno distinto por fila para no chocar el UNIQUE.
                "PENDIENTE".equals(anchorStatus) ? null : String.format("0x%064x", id.getMostSignificantBits() & Long.MAX_VALUE),
                creadaEn);
        return id;
    }

    private void evidencia(UUID queja, String cid, String creadaEn) {
        jdbc.update("""
                INSERT INTO evidencia (id, queja_id, ipfs_cid, mime_type, created_at)
                VALUES (?, ?, ?, 'image/jpeg', ?::timestamptz)""", UUID.randomUUID(), queja, cid, creadaEn);
    }
}
