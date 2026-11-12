package ar.libroabierto.complaint.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.libroabierto.PostgresTestcontainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
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

/** HU-01 por HTTP: autenticación, validación, límite diario y alta de la queja en PENDIENTE. */
@SpringBootTest(properties = {
        "libroabierto.worker.habilitado=false",
        "libroabierto.listener.habilitado=false",
        "libroabierto.triage.habilitado=false",
        "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
        "libroabierto.blockchain.relayer-private-key=59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
        "libroabierto.auth.jwt-secret=" + RegistroDeQuejaApiTest.SECRETO})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class RegistroDeQuejaApiTest {

    static final String SECRETO = "secreto-de-prueba-de-al-menos-32-bytes-para-hs256";
    private static final String CLAVE = "clave-de-prueba";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private final String hash = new BCryptPasswordEncoder().encode(CLAVE);

    private UUID ciudadano;

    // Sin @Transactional en la clase: cada pedido confirma su transacción como en producción, y se limpia a mano.
    @BeforeEach
    void usuarios() {
        jdbc.update("DELETE FROM queja");
        jdbc.update("DELETE FROM empresa");
        jdbc.update("DELETE FROM usuario");
        ciudadano = usuario("ciudadana@example.test", "CIUDADANO", true);
    }

    @Test
    void elLoginDevuelveUnTokenYElRol() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ciudadana@example.test\", \"password\": \"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CIUDADANO"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void unLoginConClaveIncorrectaDevuelve401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ciudadana@example.test\", \"password\": \"otra\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unCiudadanoVerificadoRegistraUnaQuejaQueQuedaPendiente() throws Exception {
        String respuesta = mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.anchorStatus").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(json.readTree(respuesta).get("id").asText());
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT q.usuario_id, q.anchor_status, q.estado, q.categoria_declarada, q.monto_reclamado,
                       q.fecha_hecho::text AS fecha_hecho, e.cuit, e.razon_social
                FROM queja q JOIN empresa e ON e.id = q.empresa_id WHERE q.id = ?""", id);
        assertThat(fila).containsEntry("usuario_id", ciudadano)
                .containsEntry("anchor_status", "PENDIENTE")
                .containsEntry("estado", "REGISTRADA")
                .containsEntry("categoria_declarada", "telecomunicaciones")
                .containsEntry("fecha_hecho", "2026-10-15")
                .containsEntry("cuit", "30712345671")
                .containsEntry("razon_social", "Empresa Ficticia S.A.");
        assertThat(fila.get("monto_reclamado").toString()).isEqualTo("15000.00");
    }

    @Test
    void laRespuestaIndicaDondeConsultarLaQueja() throws Exception {
        var respuesta = mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse();

        String id = json.readTree(respuesta.getContentAsString()).get("id").asText();
        assertThat(respuesta.getHeader("Location")).isEqualTo("/api/quejas/" + id);
    }

    @Test
    void sinTokenDevuelve401YNoCreaNada() throws Exception {
        mvc.perform(post("/api/quejas").contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isUnauthorized());

        assertThat(cantidadDeQuejas()).isZero();
    }

    @Test
    void unTokenFirmadoConOtraClaveDevuelve401() throws Exception {
        String falso = token("otro-secreto-de-al-menos-32-bytes-para-hs256!!", ciudadano, "CIUDADANO");

        mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + falso)
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unCuitConDigitoVerificadorInvalidoDevuelve400ConElCampoYNoCreaLaQueja() throws Exception {
        mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345678")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cuitEmpresa").isNotEmpty());

        assertThat(cantidadDeQuejas()).isZero();
    }

    @Test
    void unJsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cuitEmpresa\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unFuncionarioNoPuedeRegistrarQuejas() throws Exception {
        usuario("funcionaria@example.test", "FUNCIONARIO", true);

        mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + login("funcionaria@example.test"))
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unCiudadanoSinIdentidadVerificadaRecibe403() throws Exception {
        usuario("sin-verificar@example.test", "CIUDADANO", false);

        mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + login("sin-verificar@example.test"))
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isForbidden());
        assertThat(cantidadDeQuejas()).isZero();
    }

    @Test
    void laSextaQuejaDelDiaDevuelve429() throws Exception {
        String token = login();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(post("/api/quejas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(quejaValida("30712345671")))
                .andExpect(status().isTooManyRequests());
        assertThat(cantidadDeQuejas()).isEqualTo(5);
    }

    private String login() throws Exception {
        return login("ciudadana@example.test");
    }

    private String login(String email) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cuerpo = json.readTree(respuesta);
        return cuerpo.get("token").asText();
    }

    private UUID usuario(String email, String rol, boolean verificado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, identidad_verificada)
                VALUES (?, ?, ?, ?, 'Persona Ficticia', ?)""", id, email, hash, rol, verificado);
        return id;
    }

    private long cantidadDeQuejas() {
        return jdbc.queryForObject("SELECT count(*) FROM queja", Long.class);
    }

    private static String quejaValida(String cuit) {
        return """
                {"cuitEmpresa": "%s", "razonSocial": "Empresa Ficticia S.A.", "categoria": "telecomunicaciones",
                 "descripcion": "Corte de servicio de internet durante 15 días.", "montoReclamado": 15000,
                 "fechaHecho": "2026-10-15"}""".formatted(cuit);
    }

    private static String token(String secreto, UUID usuario, String rol) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(usuario.toString())
                .claim("rol", rol)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build());
        jwt.sign(new MACSigner(secreto.getBytes()));
        return jwt.serialize();
    }
}
