package ar.libroabierto.complaint.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.support.ModeloLocalDeTest.ModeloLocalFalso;
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
import org.springframework.test.web.servlet.ResultActions;

/** HU-05 por HTTP: la empresa ve las quejas en su contra y responde una vez cada una. */
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
class RespuestaApiTest {

    private static final String CLAVE = "clave-de-prueba";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ModeloLocalFalso modelo;

    private final String hash = new BCryptPasswordEncoder().encode(CLAVE);
    private UUID ciudadano;
    private UUID empresa;
    private UUID otraEmpresa;
    private UUID representante;
    private long siguienteOnchainId = 1;

    @BeforeEach
    void datos() {
        for (String tabla : new String[] {"respuesta", "cambio_estado", "transaccion_blockchain", "triage", "evidencia",
                "queja", "usuario", "empresa"}) {
            jdbc.update("DELETE FROM " + tabla);
        }
        ciudadano = usuario("ciudadana@example.test", "CIUDADANO", null);
        usuario("funcionaria@example.test", "FUNCIONARIO", null);
        empresa = empresa("30712345671", "Telecomunicaciones del Sur S.A.");
        otraEmpresa = empresa("30500010912", "Banco Ficticio S.A.");
        representante = usuario("representante@example.test", "EMPRESA", empresa);
        usuario("otra@example.test", "EMPRESA", otraEmpresa);
        modelo.reiniciar();
    }

    @Test
    void laEmpresaRespondeYLaRespuestaQuedaPendienteDePublicacion() throws Exception {
        UUID queja = confirmada(empresa, "2026-10-01T10:00:00Z");

        String cuerpo = responder("representante@example.test", queja, "  Ya restablecimos el servicio.  ")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(new ObjectMapper().readTree(cuerpo).get("id").asText());
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT queja_id, autor_id, texto, anchor_status, content_hash FROM respuesta WHERE id = ?", id);
        assertThat(fila).containsEntry("queja_id", queja)
                .containsEntry("autor_id", representante)
                .containsEntry("texto", "Ya restablecimos el servicio.")
                .containsEntry("anchor_status", "PENDIENTE")
                .containsEntry("content_hash", null);
    }

    @Test
    void laVistaPublicaMuestraQueHayRespuestaSinElTextoNiElAutor() throws Exception {
        UUID queja = confirmada(empresa, "2026-10-01T10:00:00Z");
        mvc.perform(get("/api/quejas/" + queja)).andExpect(jsonPath("$.respuesta").doesNotExist());

        responder("representante@example.test", queja, "Texto de la respuesta.").andExpect(status().isAccepted());

        String publico = mvc.perform(get("/api/quejas/" + queja))
                .andExpect(jsonPath("$.respuesta.anchorStatus").value("PENDIENTE"))
                .andExpect(jsonPath("$.respuesta.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(publico).doesNotContain("Texto de la respuesta", representante.toString(), "representante@");
    }

    @Test
    void hayUnaSolaRespuestaPorQueja() throws Exception {
        UUID queja = confirmada(empresa, "2026-10-01T10:00:00Z");
        responder("representante@example.test", queja, "Primera.").andExpect(status().isAccepted());

        responder("representante@example.test", queja, "Segunda.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuesta", Long.class)).isOne();
    }

    @Test
    void otraEmpresaNoPuedeResponderYTampocoUnCiudadanoNiUnFuncionario() throws Exception {
        UUID queja = confirmada(empresa, "2026-10-01T10:00:00Z");

        responder("otra@example.test", queja, "No es mía.").andExpect(status().isForbidden());
        responder("ciudadana@example.test", queja, "Texto.").andExpect(status().isForbidden());
        responder("funcionaria@example.test", queja, "Texto.").andExpect(status().isForbidden());
        mvc.perform(post("/api/quejas/" + queja + "/respuesta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\": \"Sin token.\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuesta", Long.class)).isZero();
    }

    @Test
    void unaQuejaSinAnclarDevuelve409YUnaInexistente404() throws Exception {
        responder("representante@example.test", noConfirmada(empresa), "Texto.").andExpect(status().isConflict());
        responder("representante@example.test", UUID.randomUUID(), "Texto.").andExpect(status().isNotFound());
    }

    @Test
    void unTextoVacioODemasiadoLargoDevuelve400() throws Exception {
        UUID queja = confirmada(empresa, "2026-10-01T10:00:00Z");

        responder("representante@example.test", queja, "   ").andExpect(status().isBadRequest());
        responder("representante@example.test", queja, "x".repeat(3001)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quejas/" + queja + "/respuesta")
                        .header("Authorization", "Bearer " + login("representante@example.test"))
                        .contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laBandejaTraeSoloLasQuejasDeSuEmpresaConLasPorResponderPrimero() throws Exception {
        UUID respondida = confirmada(empresa, "2026-10-05T10:00:00Z");
        UUID porResponder = confirmada(empresa, "2026-10-01T10:00:00Z");
        UUID sinAnclar = noConfirmada(empresa);
        confirmada(otraEmpresa, "2026-10-02T10:00:00Z");
        responder("representante@example.test", respondida, "Ya respondida.").andExpect(status().isAccepted());

        String cuerpo = mvc.perform(get("/api/empresa/quejas")
                        .header("Authorization", "Bearer " + login("representante@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.cuit").value("30712345671"))
                .andExpect(jsonPath("$.empresa.razonSocial").value("Telecomunicaciones del Sur S.A."))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(porResponder.toString()))
                .andExpect(jsonPath("$.items[0].anclada").value(true))
                .andExpect(jsonPath("$.items[0].respuesta").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(respondida.toString()))
                .andExpect(jsonPath("$.items[1].respuesta").value("PENDIENTE"))
                .andExpect(jsonPath("$.items[2].id").value(sinAnclar.toString()))
                .andExpect(jsonPath("$.items[2].anclada").value(false))
                .andReturn().getResponse().getContentAsString();
        // La empresa nunca ve quién se quejó.
        assertThat(cuerpo).doesNotContain(ciudadano.toString(), "ciudadana@", "Persona Ficticia");
    }

    @Test
    void unaRespuestaConDatosDelConsumidorDevuelve422HastaQueLaEmpresaConfirme() throws Exception {
        UUID queja = confirmada(empresa, "2026-10-01T10:00:00Z");
        String texto = "Le reintegramos el cargo a la clienta, DNI 30.123.456.";

        responder("representante@example.test", queja, texto)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.datosPersonales.campo").value("texto"))
                .andExpect(jsonPath("$.datosPersonales.hallazgos[0].tipo").value("DNI"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuesta", Long.class)).isZero();

        mvc.perform(post("/api/quejas/" + queja + "/respuesta")
                        .header("Authorization", "Bearer " + login("representante@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                Map.of("texto", texto, "confirmarDatosPersonales", true))))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuesta", Long.class)).isOne();
    }

    @Test
    void laBandejaEsSoloParaEmpresas() throws Exception {
        mvc.perform(get("/api/empresa/quejas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/empresa/quejas").header("Authorization", "Bearer " + login("funcionaria@example.test")))
                .andExpect(status().isForbidden());
    }

    private ResultActions responder(String email, UUID queja, String texto) throws Exception {
        return mvc.perform(post("/api/quejas/" + queja + "/respuesta")
                .header("Authorization", "Bearer " + login(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("texto", texto))));
    }

    private String login(String email) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + CLAVE + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(respuesta).get("token").asText();
    }

    private UUID empresa(String cuit, String razonSocial) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, ?, ?)", id, cuit, razonSocial);
        return id;
    }

    private UUID usuario(String email, String rol, UUID empresaId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, identidad_verificada, empresa_id)
                VALUES (?, ?, ?, ?, 'Persona Ficticia', true, ?)""", id, email, hash, rol, empresaId);
        return id;
    }

    private UUID confirmada(UUID empresaId, String creadaEn) {
        UUID id = UUID.randomUUID();
        long onchainId = siguienteOnchainId++;
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho,
                                   anchor_status, ipfs_cid, content_hash, onchain_id, created_at)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Descripción', DATE '2026-09-30', 'CONFIRMADA', ?, ?, ?,
                        ?::timestamptz)""",
                id, ciudadano, empresaId, "bafy-" + id, String.format("0x%064x", onchainId), onchainId, creadaEn);
        return id;
    }

    private UUID noConfirmada(UUID empresaId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho, created_at)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Descripción', DATE '2026-09-30', '2026-10-03T10:00:00Z')""",
                id, ciudadano, empresaId);
        return id;
    }
}
