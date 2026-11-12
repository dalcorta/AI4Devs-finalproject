package ar.libroabierto.complaint.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.PostgresTestcontainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterios de aceptación del Ticket 1: la migración corre limpia y las restricciones se cumplen en la base. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainer.class)
class EsquemaTest {

    private static final String HASH = "0x" + "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAplicaLaMigracionInicial() {
        Integer aplicadas = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'", Integer.class);
        assertThat(aplicadas).isEqualTo(1);
    }

    @Test
    void cuitDuplicadoEsRechazado() {
        empresa("30712345678");

        assertThatThrownBy(() -> empresa("30712345678")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cuitConFormatoInvalidoEsRechazado() {
        assertThatThrownBy(() -> empresa("30-71234567")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void triageDuplicadoParaLaMismaQuejaEsRechazado() {
        UUID queja = queja(null);
        triage(queja);

        assertThatThrownBy(() -> triage(queja)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void contentHashDuplicadoEsRechazado() {
        queja(HASH);

        assertThatThrownBy(() -> queja(HASH)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anchorStatusFueraDelEnumEsRechazado() {
        UUID queja = queja(null);

        assertThatThrownBy(() -> jdbc.update("UPDATE queja SET anchor_status = 'INVENTADO' WHERE id = ?", queja))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void soloUnUsuarioConRolEmpresaPuedeTenerEmpresaAsociada() {
        UUID empresa = empresa("30500010912");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, empresa_id)
                VALUES (?, 'ciudadano@example.test', 'x', 'CIUDADANO', 'Persona Ficticia', ?)""",
                UUID.randomUUID(), empresa)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID empresa(String cuit) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, ?, 'Empresa Ficticia S.A.')", id, cuit);
        return id;
    }

    private UUID queja(String contentHash) {
        UUID usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo)
                VALUES (?, ?, 'x', 'CIUDADANO', 'Persona Ficticia')""", usuario, usuario + "@example.test");
        UUID empresa = empresa(String.valueOf(30000000000L + Math.abs(usuario.getMostSignificantBits() % 1_000_000_000L)));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho, content_hash)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Descripción', DATE '2026-10-15', ?)""",
                id, usuario, empresa, contentHash);
        return id;
    }

    private void triage(UUID queja) {
        jdbc.update("""
                INSERT INTO triage (id, queja_id, categoria_sugerida, resumen, prioridad, articulos_sugeridos, modelo)
                VALUES (?, ?, 'telecomunicaciones', 'Resumen', 'ALTA', '["art. 10 bis"]'::jsonb, 'modelo-test')""",
                UUID.randomUUID(), queja);
    }
}
