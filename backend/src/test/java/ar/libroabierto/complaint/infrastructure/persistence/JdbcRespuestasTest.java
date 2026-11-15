package ar.libroabierto.complaint.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.complaint.application.RespuestaPendiente;
import ar.libroabierto.complaint.application.Respuestas;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.DatosDeRespuesta;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.listener.application.EventoRespuestaRegistrada;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, JdbcRespuestas.class})
class JdbcRespuestasTest {

    private static final String HASH_QUEJA = "0x" + "a".repeat(64);
    private static final String HASH = "0x" + "b".repeat(64);
    private static final String TX = "0x" + "c".repeat(64);
    private static final Instant CREADA = Instant.parse("2026-11-14T15:00:00.123456Z");

    @Autowired
    private JdbcRespuestas respuestas;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID empresa;
    private UUID representante;
    private UUID queja;

    @BeforeEach
    void datos() {
        UUID ciudadano = usuario("CIUDADANO", null);
        empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, '30712345671', 'Empresa S.A.')", empresa);
        representante = usuario("EMPRESA", empresa);
        queja = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho,
                                   content_hash, anchor_status, onchain_id)
                VALUES (?, ?, ?, 'bancos', 'Descripción', DATE '2026-10-01', ?, 'CONFIRMADA', 7)""",
                queja, ciudadano, empresa, HASH_QUEJA);
    }

    @Test
    void laEmpresaDeUnUsuarioSoloExisteParaElRolEmpresa() {
        assertThat(respuestas.empresaDe(representante)).contains(empresa);
        assertThat(respuestas.empresaDe(usuario("FUNCIONARIO", null))).isEmpty();
        assertThat(respuestas.empresaDe(UUID.randomUUID())).isEmpty();
    }

    @Test
    void laSituacionDiceDeQuienEsLaQuejaSiEstaAncladaYSiYaFueRespondida() {
        assertThat(respuestas.situacion(queja))
                .contains(new Respuestas.Situacion(empresa, AnchorStatus.CONFIRMADA, false));
        respuestas.registrar(nueva());

        assertThat(respuestas.situacion(queja))
                .contains(new Respuestas.Situacion(empresa, AnchorStatus.CONFIRMADA, true));
        assertThat(respuestas.situacion(UUID.randomUUID())).isEmpty();
    }

    @Test
    void unaSegundaRespuestaALaMismaQuejaNoSeGuarda() {
        assertThat(respuestas.registrar(nueva())).isTrue();
        assertThat(respuestas.registrar(nueva())).isFalse();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuesta", Long.class)).isOne();
    }

    @Test
    void lasPendientesTraenLoNecesarioParaArmarElDocumento() {
        Respuestas.NuevaRespuesta nueva = nueva();
        respuestas.registrar(nueva);

        assertThat(respuestas.pendientesDeAnclaje()).containsExactly(new RespuestaPendiente(nueva.id(),
                new DatosDeRespuesta(7, HASH_QUEJA, new Empresa("30712345671", "Empresa S.A."), "Texto de la respuesta.",
                        Instant.parse("2026-11-14T15:00:00.123456Z")), null, null));
    }

    @Test
    void publicarYEnviarLaSacanDeLasPendientesYRegistranLaTransaccion() {
        Respuestas.NuevaRespuesta nueva = nueva();
        respuestas.registrar(nueva);

        respuestas.registrarPublicacion(nueva.id(), "bafy-respuesta", HASH);
        assertThat(respuestas.pendientesDeAnclaje()).singleElement()
                .satisfies(p -> assertThat(p.contentHash()).isEqualTo(HASH));
        assertThatThrownBy(() -> respuestas.registrarPublicacion(nueva.id(), "bafy-otra", "0x" + "d".repeat(64)))
                .isInstanceOf(IllegalStateException.class);

        respuestas.marcarEnviada(nueva.id(), TX);
        respuestas.marcarEnviada(nueva.id(), TX);

        assertThat(respuestas.pendientesDeAnclaje()).isEmpty();
        assertThat(jdbc.queryForMap("SELECT anchor_status, tx_hash FROM respuesta WHERE id = ?", nueva.id()))
                .containsEntry("anchor_status", "ENVIADA").containsEntry("tx_hash", TX);
        assertThat(jdbc.queryForList("SELECT tipo, referencia_id, estado FROM transaccion_blockchain")).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("tipo", "RESPUESTA")
                        .containsEntry("referencia_id", nueva.id()).containsEntry("estado", "ENVIADA"));
    }

    @Test
    void unaQuejaSinAnclarNoTieneRespuestasPendientesDeAnclaje() {
        respuestas.registrar(nueva());
        jdbc.update("UPDATE queja SET anchor_status = 'ENVIADA', onchain_id = NULL WHERE id = ?", queja);

        assertThat(respuestas.pendientesDeAnclaje()).isEmpty();
    }

    @Test
    void elEventoConfirmaLaRespuestaYSuTransaccionUnaSolaVez() {
        Respuestas.NuevaRespuesta nueva = nueva();
        respuestas.registrar(nueva);
        respuestas.registrarPublicacion(nueva.id(), "bafy-respuesta", HASH);
        respuestas.marcarEnviada(nueva.id(), TX);

        respuestas.confirmar(new EventoRespuestaRegistrada(7, HASH, "bafy-respuesta", 321, TX));
        respuestas.confirmar(new EventoRespuestaRegistrada(7, HASH, "bafy-respuesta", 321, TX));

        assertThat(jdbc.queryForObject("SELECT anchor_status FROM respuesta WHERE id = ?", String.class, nueva.id()))
                .isEqualTo("CONFIRMADA");
        assertThat(jdbc.queryForList("SELECT estado, block_number FROM transaccion_blockchain")).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("estado", "CONFIRMADA")
                        .containsEntry("block_number", 321L));
    }

    @Test
    void siElWorkerNoLlegoAMarcarlaEnviadaElEventoLaConfirmaIgual() {
        Respuestas.NuevaRespuesta nueva = nueva();
        respuestas.registrar(nueva);
        respuestas.registrarPublicacion(nueva.id(), "bafy-respuesta", HASH);

        respuestas.confirmar(new EventoRespuestaRegistrada(7, HASH, "bafy-respuesta", 321, TX));

        assertThat(jdbc.queryForMap("SELECT anchor_status, tx_hash FROM respuesta WHERE id = ?", nueva.id()))
                .containsEntry("anchor_status", "CONFIRMADA").containsEntry("tx_hash", TX);
        assertThat(respuestas.pendientesDeAnclaje()).isEmpty();
    }

    @Test
    void unEventoDeOtraQuejaConElMismoHashNoConfirmaNada() {
        Respuestas.NuevaRespuesta nueva = nueva();
        respuestas.registrar(nueva);
        respuestas.registrarPublicacion(nueva.id(), "bafy-respuesta", HASH);

        respuestas.confirmar(new EventoRespuestaRegistrada(8, HASH, "bafy-respuesta", 321, TX));

        assertThat(jdbc.queryForObject("SELECT anchor_status FROM respuesta WHERE id = ?", String.class, nueva.id()))
                .isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaccion_blockchain", Long.class)).isZero();
    }

    // Un rechazo aborta la transacción del test: cada restricción va en su propio test.
    @Test
    void elEsquemaRechazaUnTextoVacio() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO respuesta (id, queja_id, autor_id, texto) VALUES (?, ?, ?, '')""",
                UUID.randomUUID(), queja, representante)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elEsquemaRechazaUnaRespuestaEnviadaSinHash() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO respuesta (id, queja_id, autor_id, texto, anchor_status) VALUES (?, ?, ?, 'x', 'ENVIADA')""",
                UUID.randomUUID(), queja, representante)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Respuestas.NuevaRespuesta nueva() {
        return new Respuestas.NuevaRespuesta(UUID.randomUUID(), queja, representante, "Texto de la respuesta.", CREADA);
    }

    private UUID usuario(String rol, UUID empresaId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, empresa_id)
                VALUES (?, ?, 'x', ?, 'Persona Ficticia', ?)""", id, id + "@example.test", rol, empresaId);
        return id;
    }
}
