package ar.libroabierto.complaint.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.libroabierto.PostgresTestcontainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** HU-04 por HTTP: bandeja priorizada del funcionario y cambio de estado con su historial. */
@SpringBootTest(properties = {
        "libroabierto.worker.habilitado=false",
        "libroabierto.listener.habilitado=false",
        "libroabierto.triage.habilitado=false",
        "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
        "libroabierto.blockchain.relayer-private-key=59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
        "libroabierto.blockchain.officer-private-key=7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6",
        "libroabierto.auth.jwt-secret=secreto-de-prueba-de-al-menos-32-bytes-para-hs256"})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class PanelApiTest {

    private static final String CLAVE = "clave-de-prueba";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final String hash = new BCryptPasswordEncoder().encode(CLAVE);
    private UUID ciudadano;
    private UUID funcionario;
    private UUID empresa;
    private long siguienteOnchainId = 1;

    @BeforeEach
    void datos() {
        for (String tabla : new String[] {"cambio_estado", "transaccion_blockchain", "triage", "evidencia", "queja",
                "empresa", "usuario"}) {
            jdbc.update("DELETE FROM " + tabla);
        }
        ciudadano = usuario("ciudadana@example.test", "CIUDADANO");
        funcionario = usuario("funcionaria@example.test", "FUNCIONARIO");
        empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, '30712345671', 'Telecomunicaciones del Sur S.A.')",
                empresa);
    }

    @Test
    void laBandejaPoneLoManualPrimeroYDespuesOrdenaPorPrioridadYAntiguedad() throws Exception {
        UUID baja = confirmada("2026-10-01T10:00:00Z");
        triage(baja, "SUGERIDO", "BAJA");
        UUID altaNueva = confirmada("2026-10-05T10:00:00Z");
        triage(altaNueva, "SUGERIDO", "ALTA");
        UUID altaVieja = confirmada("2026-10-02T10:00:00Z");
        triage(altaVieja, "SUGERIDO", "ALTA");
        UUID media = confirmada("2026-10-03T10:00:00Z");
        triage(media, "SUGERIDO", "MEDIA");
        UUID manual = confirmada("2026-10-09T10:00:00Z");
        triage(manual, "MANUAL", null);
        UUID sinTriage = confirmada("2026-09-01T10:00:00Z");
        noConfirmada("2026-08-01T10:00:00Z");

        mvc.perform(get("/api/panel/quejas").header("Authorization", "Bearer " + login("funcionaria@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].id").value(manual.toString()))
                .andExpect(jsonPath("$.items[1].id").value(altaVieja.toString()))
                .andExpect(jsonPath("$.items[2].id").value(altaNueva.toString()))
                .andExpect(jsonPath("$.items[3].id").value(media.toString()))
                .andExpect(jsonPath("$.items[4].id").value(baja.toString()))
                .andExpect(jsonPath("$.items[5].id").value(sinTriage.toString()));
    }

    @Test
    void cadaItemTraeElTriageComoSugerenciaYSuEstado() throws Exception {
        UUID sugerida = confirmada("2026-10-01T10:00:00Z");
        triage(sugerida, "SUGERIDO", "ALTA");
        UUID manual = confirmada("2026-10-02T10:00:00Z");
        triage(manual, "MANUAL", null);
        UUID pendiente = confirmada("2026-10-03T10:00:00Z");

        mvc.perform(get("/api/panel/quejas").header("Authorization", "Bearer " + login("funcionaria@example.test")))
                .andExpect(jsonPath("$.items[0].triageEstado").value("MANUAL"))
                .andExpect(jsonPath("$.items[0].prioridad").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(sugerida.toString()))
                .andExpect(jsonPath("$.items[1].empresa").value("Telecomunicaciones del Sur S.A."))
                .andExpect(jsonPath("$.items[1].triageEstado").value("SUGERIDO"))
                .andExpect(jsonPath("$.items[1].categoriaSugerida").value("telecomunicaciones"))
                .andExpect(jsonPath("$.items[1].resumen").value("Resumen de prueba."))
                .andExpect(jsonPath("$.items[1].prioridad").value("ALTA"))
                .andExpect(jsonPath("$.items[1].articulosSugeridos[0]").value("19"))
                .andExpect(jsonPath("$.items[1].estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-10-01T10:00:00Z"))
                .andExpect(jsonPath("$.items[2].id").value(pendiente.toString()))
                .andExpect(jsonPath("$.items[2].triageEstado").value("PENDIENTE"));
    }

    @Test
    void laBandejaSeFiltraPorEstado() throws Exception {
        confirmada("2026-10-01T10:00:00Z");
        UUID enRevision = confirmada("2026-10-02T10:00:00Z");
        jdbc.update("UPDATE queja SET estado = 'EN_REVISION' WHERE id = ?", enRevision);

        mvc.perform(get("/api/panel/quejas").param("estado", "EN_REVISION")
                        .header("Authorization", "Bearer " + login("funcionaria@example.test")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(enRevision.toString()));
        mvc.perform(get("/api/panel/quejas").param("estado", "INVENTADO")
                        .header("Authorization", "Bearer " + login("funcionaria@example.test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laBandejaEsSoloParaFuncionarios() throws Exception {
        mvc.perform(get("/api/panel/quejas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/panel/quejas").header("Authorization", "Bearer " + login("ciudadana@example.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambiarElEstadoQuedaEnElHistorialConActorYComentarioYSeVeEnLaVistaPublica() throws Exception {
        UUID queja = confirmada("2026-10-01T10:00:00Z");

        mvc.perform(patch("/api/quejas/" + queja + "/estado")
                        .header("Authorization", "Bearer " + login("funcionaria@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"EN_REVISION\", \"comentario\": \"Se pidió documentación.\"}"))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT estado FROM queja WHERE id = ?", String.class, queja))
                .isEqualTo("EN_REVISION");
        Map<String, Object> cambio = jdbc.queryForMap(
                "SELECT actor_id, estado_anterior, estado_nuevo, comentario FROM cambio_estado WHERE queja_id = ?", queja);
        assertThat(cambio).containsEntry("actor_id", funcionario)
                .containsEntry("estado_anterior", "REGISTRADA")
                .containsEntry("estado_nuevo", "EN_REVISION")
                .containsEntry("comentario", "Se pidió documentación.");

        String publico = mvc.perform(get("/api/quejas/" + queja))
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.historialEstados[0].estadoNuevo").value("EN_REVISION"))
                .andReturn().getResponse().getContentAsString();
        assertThat(publico).doesNotContain("Se pidió documentación", funcionario.toString());
    }

    @Test
    void unaTransicionNoPermitidaDevuelve409YNoCambiaNada() throws Exception {
        UUID queja = confirmada("2026-10-01T10:00:00Z");

        mvc.perform(patch("/api/quejas/" + queja + "/estado")
                        .header("Authorization", "Bearer " + login("funcionaria@example.test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"RESUELTA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT estado FROM queja WHERE id = ?", String.class, queja))
                .isEqualTo("REGISTRADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cambio_estado", Long.class)).isZero();
    }

    @Test
    void unaQuejaSinAnclarDevuelve409YUnaInexistente404() throws Exception {
        UUID sinAnclar = noConfirmada("2026-10-01T10:00:00Z");
        String token = login("funcionaria@example.test");

        mvc.perform(patch("/api/quejas/" + sinAnclar + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"EN_REVISION\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/quejas/" + UUID.randomUUID() + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"EN_REVISION\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void datosInvalidosDevuelven400() throws Exception {
        UUID queja = confirmada("2026-10-01T10:00:00Z");
        String token = login("funcionaria@example.test");

        mvc.perform(patch("/api/quejas/" + queja + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"INVENTADO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/quejas/" + queja + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comentario\": \"sin estado\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/quejas/" + queja + "/estado").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"EN_REVISION\", \"comentario\": \"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unCiudadanoNoPuedeCambiarEstados() throws Exception {
        UUID queja = confirmada("2026-10-01T10:00:00Z");

        mvc.perform(patch("/api/quejas/" + queja + "/estado")
                        .header("Authorization", "Bearer " + login("ciudadana@example.test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"EN_REVISION\"}"))
                .andExpect(status().isForbidden());
    }

    private String login(String email) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + CLAVE + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(respuesta).get("token").asText();
    }

    private UUID usuario(String email, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, identidad_verificada)
                VALUES (?, ?, ?, ?, 'Persona Ficticia', true)""", id, email, hash, rol);
        return id;
    }

    private UUID confirmada(String creadaEn) {
        UUID id = UUID.randomUUID();
        long onchainId = siguienteOnchainId++;
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho,
                                   anchor_status, ipfs_cid, content_hash, onchain_id, created_at)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Descripción', DATE '2026-09-30', 'CONFIRMADA', ?, ?, ?,
                        ?::timestamptz)""",
                id, ciudadano, empresa, "bafy-" + id, String.format("0x%064x", onchainId), onchainId, creadaEn);
        return id;
    }

    private UUID noConfirmada(String creadaEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho, created_at)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Descripción', DATE '2026-09-30', ?::timestamptz)""",
                id, ciudadano, empresa, creadaEn);
        return id;
    }

    private void triage(UUID queja, String estado, String prioridad) {
        if ("MANUAL".equals(estado)) {
            jdbc.update("INSERT INTO triage (id, queja_id, estado, modelo, intentos) VALUES (?, ?, 'MANUAL', 'modelo', 3)",
                    UUID.randomUUID(), queja);
            return;
        }
        jdbc.update("""
                INSERT INTO triage (id, queja_id, estado, categoria_sugerida, resumen, prioridad, articulos_sugeridos,
                                    modelo)
                VALUES (?, ?, 'SUGERIDO', 'telecomunicaciones', 'Resumen de prueba.', ?, '["19", "10 bis"]'::jsonb,
                        'modelo')""", UUID.randomUUID(), queja, prioridad);
    }
}
