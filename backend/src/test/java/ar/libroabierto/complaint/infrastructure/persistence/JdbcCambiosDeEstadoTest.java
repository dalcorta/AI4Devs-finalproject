package ar.libroabierto.complaint.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.complaint.application.CambioPendiente;
import ar.libroabierto.complaint.application.CambiosDeEstado;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.CambioDeEstado;
import ar.libroabierto.complaint.domain.EstadoQueja;
import ar.libroabierto.listener.application.EventoEstadoCambiado;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, JdbcCambiosDeEstado.class})
class JdbcCambiosDeEstadoTest {

    private static final String TX = "0x" + "c".repeat(64);

    @Autowired
    private JdbcCambiosDeEstado cambios;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID funcionario;
    private UUID queja;

    @BeforeEach
    void datos() {
        UUID ciudadano = usuario("CIUDADANO");
        funcionario = usuario("FUNCIONARIO");
        UUID empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, '30712345671', 'Empresa S.A.')", empresa);
        queja = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho,
                                   content_hash, anchor_status, onchain_id)
                VALUES (?, ?, ?, 'bancos', 'Descripción', DATE '2026-10-01', ?, 'CONFIRMADA', 7)""",
                queja, ciudadano, empresa, "0x" + "a".repeat(64));
    }

    @Test
    void laSituacionTraeIdOnChainAnclajeYEstado() {
        assertThat(cambios.situacion(queja))
                .contains(new CambiosDeEstado.Situacion(7L, AnchorStatus.CONFIRMADA, EstadoQueja.REGISTRADA));
        assertThat(cambios.situacion(UUID.randomUUID())).isEmpty();
    }

    @Test
    void registrarGuardaElHistorialYActualizaElEstado() {
        CambioDeEstado cambio = cambio(EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, "Comentario interno");

        assertThat(cambios.registrar(cambio)).isTrue();

        assertThat(jdbc.queryForObject("SELECT estado FROM queja WHERE id = ?", String.class, queja))
                .isEqualTo("EN_REVISION");
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT queja_id, estado_anterior, estado_nuevo, actor_id, comentario, tx_hash, created_at
                FROM cambio_estado WHERE id = ?""", cambio.id());
        assertThat(fila).containsEntry("queja_id", queja)
                .containsEntry("estado_anterior", "REGISTRADA")
                .containsEntry("estado_nuevo", "EN_REVISION")
                .containsEntry("actor_id", funcionario)
                .containsEntry("comentario", "Comentario interno")
                .containsEntry("tx_hash", null);
        assertThat(((java.sql.Timestamp) fila.get("created_at")).toInstant())
                .isEqualTo(Instant.parse("2026-11-14T15:00:00Z"));
    }

    @Test
    void siElEstadoCambioEnElMedioNoGuardaNada() {
        jdbc.update("UPDATE queja SET estado = 'EN_REVISION' WHERE id = ?", queja);

        assertThat(cambios.registrar(cambio(EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, null))).isFalse();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cambio_estado", Long.class)).isZero();
    }

    @Test
    void losPendientesSalenEnOrdenYSinLosYaEnviados() {
        CambioDeEstado primero = cambio(EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, "Uno");
        cambios.registrar(primero);
        CambioDeEstado segundo = new CambioDeEstado(UUID.randomUUID(), queja, EstadoQueja.EN_REVISION,
                EstadoQueja.DERIVADA, funcionario, null, Instant.parse("2026-11-14T16:00:00Z"));
        cambios.registrar(segundo);

        assertThat(cambios.pendientesDeAnclaje()).containsExactly(
                new CambioPendiente(primero.id(), queja, 7, EstadoQueja.EN_REVISION, "Uno"),
                new CambioPendiente(segundo.id(), queja, 7, EstadoQueja.DERIVADA, null));

        cambios.marcarEnviado(primero.id(), TX);

        assertThat(cambios.pendientesDeAnclaje()).extracting(CambioPendiente::cambioId).containsExactly(segundo.id());
    }

    @Test
    void marcarEnviadoGuardaElTxHashYRegistraLaTransaccionUnaSolaVez() {
        CambioDeEstado cambio = cambio(EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, null);
        cambios.registrar(cambio);

        cambios.marcarEnviado(cambio.id(), TX);
        cambios.marcarEnviado(cambio.id(), TX);

        assertThat(jdbc.queryForObject("SELECT tx_hash FROM cambio_estado WHERE id = ?", String.class, cambio.id()))
                .isEqualTo(TX);
        List<Map<String, Object>> transacciones = jdbc.queryForList(
                "SELECT tipo, referencia_id, estado FROM transaccion_blockchain WHERE tx_hash = ?", TX);
        assertThat(transacciones).hasSize(1);
        assertThat(transacciones.get(0)).containsEntry("tipo", "CAMBIO_ESTADO")
                .containsEntry("referencia_id", cambio.id())
                .containsEntry("estado", "ENVIADA");
    }

    @Test
    void elEventoConfirmaLaTransaccionDelCambioConSuBloque() {
        CambioDeEstado cambio = cambio(EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, null);
        cambios.registrar(cambio);
        cambios.marcarEnviado(cambio.id(), TX);

        cambios.confirmar(new EventoEstadoCambiado(7, EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, 321, TX));
        cambios.confirmar(new EventoEstadoCambiado(7, EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, 321, TX));

        assertThat(jdbc.queryForList("SELECT estado, block_number FROM transaccion_blockchain WHERE tx_hash = ?", TX))
                .singleElement().satisfies(fila -> assertThat(fila)
                        .containsEntry("estado", "CONFIRMADA").containsEntry("block_number", 321L));
    }

    @Test
    void siElWorkerNoLlegoAGuardarElTxHashElEventoLoCompleta() {
        CambioDeEstado cambio = cambio(EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, null);
        cambios.registrar(cambio);

        cambios.confirmar(new EventoEstadoCambiado(7, EstadoQueja.REGISTRADA, EstadoQueja.EN_REVISION, 321, TX));

        assertThat(jdbc.queryForObject("SELECT tx_hash FROM cambio_estado WHERE id = ?", String.class, cambio.id()))
                .isEqualTo(TX);
        assertThat(jdbc.queryForMap("SELECT tipo, referencia_id, estado FROM transaccion_blockchain WHERE tx_hash = ?",
                TX)).containsEntry("tipo", "CAMBIO_ESTADO").containsEntry("referencia_id", cambio.id())
                .containsEntry("estado", "CONFIRMADA");
        assertThat(cambios.pendientesDeAnclaje()).isEmpty();
    }

    private CambioDeEstado cambio(EstadoQueja anterior, EstadoQueja nuevo, String comentario) {
        return new CambioDeEstado(UUID.randomUUID(), queja, anterior, nuevo, funcionario, comentario,
                Instant.parse("2026-11-14T15:00:00Z"));
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo)
                VALUES (?, ?, 'x', ?, 'Persona Ficticia')""", id, id + "@example.test", rol);
        return id;
    }
}
