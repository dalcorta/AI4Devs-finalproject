package ar.libroabierto.triage.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.triage.domain.Prioridad;
import ar.libroabierto.triage.domain.Triage;
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
@Import({PostgresTestcontainer.class, JpaTriageRepository.class})
class JpaTriageRepositoryTest {

    @Autowired
    private JpaTriageRepository repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager em;

    private UUID queja;

    @BeforeEach
    void quejaConfirmada() {
        UUID usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo)
                VALUES (?, 'denunciante@example.test', 'x', 'CIUDADANO', 'Persona Ficticia')""", usuario);
        UUID empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, '30712345678', 'Empresa S.A.')", empresa);
        queja = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho,
                                   content_hash, anchor_status, onchain_id)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Descripción', DATE '2026-10-15', ?, 'CONFIRMADA', 1)""",
                queja, usuario, empresa, "0x" + "a".repeat(64));
    }

    @Test
    void guardaUnTriageSugeridoConSusArticulosComoJson() {
        repositorio.guardar(Triage.sugerido(queja, "telecomunicaciones", "Corte de servicio.", Prioridad.ALTA,
                List.of("19", "10 bis"), "claude-opus-5-5", 2));
        sincronizar();

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT estado, categoria_sugerida, resumen, prioridad, articulos_sugeridos::text AS articulos, modelo,
                       intentos
                FROM triage WHERE queja_id = ?""", queja);
        assertThat(fila).containsEntry("estado", "SUGERIDO")
                .containsEntry("categoria_sugerida", "telecomunicaciones")
                .containsEntry("resumen", "Corte de servicio.")
                .containsEntry("prioridad", "ALTA")
                .containsEntry("articulos", "[\"19\", \"10 bis\"]")
                .containsEntry("modelo", "claude-opus-5-5")
                .containsEntry("intentos", 2);
    }

    @Test
    void guardaUnTriageManualSinCamposDelLlm() {
        repositorio.guardar(Triage.manual(queja, "claude-opus-5-5", 3));
        sincronizar();

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, categoria_sugerida, prioridad, modelo, intentos FROM triage WHERE queja_id = ?", queja);
        assertThat(fila).containsEntry("estado", "MANUAL")
                .containsEntry("categoria_sugerida", null)
                .containsEntry("prioridad", null)
                .containsEntry("modelo", "claude-opus-5-5")
                .containsEntry("intentos", 3);
    }

    @Test
    void guardarDosVecesParaLaMismaQuejaDejaUnSoloTriageYConservaElPrimero() {
        repositorio.guardar(Triage.sugerido(queja, "telecomunicaciones", "Primero.", Prioridad.BAJA, List.of(),
                "claude-opus-5-5", 1));
        sincronizar();
        repositorio.guardar(Triage.sugerido(queja, "bancos", "Segundo.", Prioridad.ALTA, List.of(),
                "claude-opus-5-5", 1));
        sincronizar();

        assertThat(jdbc.queryForList("SELECT resumen FROM triage WHERE queja_id = ?", String.class, queja))
                .containsExactly("Primero.");
    }

    private void sincronizar() {
        em.flush();
        em.clear();
    }
}
