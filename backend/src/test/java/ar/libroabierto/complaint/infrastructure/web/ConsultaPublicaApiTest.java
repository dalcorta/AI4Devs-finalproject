package ar.libroabierto.complaint.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.libroabierto.PostgresTestcontainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** HU-02 por HTTP: búsqueda de empresas, listado y detalle públicos, sin autenticación y sin datos del denunciante. */
@SpringBootTest(properties = {
        "libroabierto.worker.habilitado=false",
        "libroabierto.listener.habilitado=false",
        "libroabierto.triage.habilitado=false",
        "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
        "libroabierto.blockchain.relayer-private-key=59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
        "libroabierto.auth.jwt-secret=secreto-de-prueba-de-al-menos-32-bytes-para-hs256"})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ConsultaPublicaApiTest {

    private static final String EMAIL = "denunciante-privado@example.test";
    private static final String NOMBRE = "Persona Ficticia Privada";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID usuario;
    private UUID banco;
    private UUID telco;

    @BeforeEach
    void datos() {
        jdbc.update("DELETE FROM cambio_estado");
        jdbc.update("DELETE FROM evidencia");
        jdbc.update("DELETE FROM triage");
        jdbc.update("DELETE FROM queja");
        jdbc.update("DELETE FROM empresa");
        jdbc.update("DELETE FROM usuario");
        usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, identidad_verificada)
                VALUES (?, ?, 'x', 'CIUDADANO', ?, true)""", usuario, EMAIL, NOMBRE);
        banco = empresa("30500010912", "Banco de la Nación Argentina");
        telco = empresa("30712345671", "Telecomunicaciones del Sur S.A.");
        empresa("30999999993", "Supermercados Norte S.R.L.");
    }

    @Test
    void buscaEmpresasPorRazonSocialSinDistinguirMayusculasNiAcentos() throws Exception {
        mvc.perform(get("/api/empresas").param("q", "nacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cuit").value("30500010912"))
                .andExpect(jsonPath("$[0].razonSocial").value("Banco de la Nación Argentina"));
    }

    @Test
    void buscaEmpresasPorCuitConOSinGuiones() throws Exception {
        mvc.perform(get("/api/empresas").param("q", "30-71234567-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cuit").value("30712345671"));
        mvc.perform(get("/api/empresas").param("q", "3071"))
                .andExpect(jsonPath("$[0].cuit").value("30712345671"));
    }

    @Test
    void unaBusquedaDemasiadoCortaEsInvalida() throws Exception {
        mvc.perform(get("/api/empresas").param("q", " a ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/empresas")).andExpect(status().isBadRequest());
        // Solo guiones: pasa el largo mínimo pero no tiene ningún dígito de CUIT.
        mvc.perform(get("/api/empresas").param("q", "---")).andExpect(status().isBadRequest());
    }

    @Test
    void losComodinesDeLikeSeBuscanComoTexto() throws Exception {
        // Sin escaparlos, "%%" devolvería todas las empresas.
        mvc.perform(get("/api/empresas").param("q", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elListadoDeUnaEmpresaTraeLosDatosDeVerificacionDeLaMasNuevaALaMasVieja() throws Exception {
        UUID confirmada = queja(banco, "2026-10-01T12:00:00Z", "CONFIRMADA", 7L);
        UUID pendiente = queja(banco, "2026-10-02T12:00:00Z", "PENDIENTE", null);
        queja(telco, "2026-10-03T12:00:00Z", "PENDIENTE", null);

        mvc.perform(get("/api/empresas/30500010912/quejas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(pendiente.toString()))
                .andExpect(jsonPath("$.items[0].onchainId").doesNotExist())
                .andExpect(jsonPath("$.items[0].contentHash").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(confirmada.toString()))
                .andExpect(jsonPath("$.items[1].onchainId").value(7))
                .andExpect(jsonPath("$.items[1].ipfsCid").value("bafy-" + confirmada))
                .andExpect(jsonPath("$.items[1].contentHash").value(hash(7)))
                .andExpect(jsonPath("$.items[1].estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.items[1].categoria").value("bancos"))
                .andExpect(jsonPath("$.items[1].fechaHecho").value("2026-09-30"));
    }

    @Test
    void elListadoSePagina() throws Exception {
        for (int i = 0; i < 21; i++) {
            queja(banco, "2026-10-01T12:%02d:00Z".formatted(i), "PENDIENTE", null);
        }

        mvc.perform(get("/api/empresas/30500010912/quejas")).andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/api/empresas/30500010912/quejas").param("page", "1"))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/empresas/30500010912/quejas").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elListadoDeUnaEmpresaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/empresas/30712345671/quejas")).andExpect(status().isOk());
        mvc.perform(get("/api/empresas/20123456786/quejas")).andExpect(status().isNotFound());
    }

    @Test
    void elDetalleTraeHistorialYEvidenciasEnOrden() throws Exception {
        UUID id = queja(banco, "2026-10-01T12:00:00Z", "CONFIRMADA", 3L);
        jdbc.update("INSERT INTO evidencia (id, queja_id, ipfs_cid, mime_type, created_at) VALUES "
                + "(?, ?, 'bafy-ev2', 'image/png', '2026-10-01T12:00:02Z'), "
                + "(?, ?, 'bafy-ev1', 'image/png', '2026-10-01T12:00:01Z')", UUID.randomUUID(), id, UUID.randomUUID(), id);
        jdbc.update("""
                INSERT INTO cambio_estado (id, queja_id, estado_anterior, estado_nuevo, actor_id, comentario, tx_hash,
                                           created_at)
                VALUES (?, ?, 'REGISTRADA', 'EN_REVISION', ?, 'Comentario interno del funcionario', ?,
                        '2026-10-05T10:00:00Z')""", UUID.randomUUID(), id, usuario, hash(99));

        mvc.perform(get("/api/quejas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.onchainId").value(3))
                .andExpect(jsonPath("$.ipfsCid").value("bafy-" + id))
                .andExpect(jsonPath("$.contentHash").value(hash(3)))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.evidenciasCids[0]").value("bafy-ev1"))
                .andExpect(jsonPath("$.evidenciasCids[1]").value("bafy-ev2"))
                .andExpect(jsonPath("$.historialEstados.length()").value(1))
                .andExpect(jsonPath("$.historialEstados[0].estadoAnterior").value("REGISTRADA"))
                .andExpect(jsonPath("$.historialEstados[0].estadoNuevo").value("EN_REVISION"))
                .andExpect(jsonPath("$.historialEstados[0].txHash").value(hash(99)))
                .andExpect(jsonPath("$.historialEstados[0].createdAt").value("2026-10-05T10:00:00Z"));
    }

    @Test
    void ningunaRespuestaPublicaExponeAlDenuncianteNiAlFuncionario() throws Exception {
        UUID id = queja(banco, "2026-10-01T12:00:00Z", "CONFIRMADA", 3L);
        jdbc.update("""
                INSERT INTO cambio_estado (id, queja_id, estado_anterior, estado_nuevo, actor_id, comentario, created_at)
                VALUES (?, ?, 'REGISTRADA', 'EN_REVISION', ?, 'Comentario interno del funcionario', now())""",
                UUID.randomUUID(), id, usuario);

        String detalle = mvc.perform(get("/api/quejas/" + id)).andReturn().getResponse().getContentAsString();
        String listado = mvc.perform(get("/api/empresas/30500010912/quejas")).andReturn().getResponse()
                .getContentAsString();

        for (String respuesta : new String[] {detalle, listado}) {
            assertThat(respuesta).doesNotContain(usuario.toString(), EMAIL, NOMBRE, "Comentario interno",
                    "usuario", "actor");
        }
    }

    @Test
    void elDetalleDeUnaQuejaPendienteNoTieneDatosDeAnclaje() throws Exception {
        UUID id = queja(banco, "2026-10-01T12:00:00Z", "PENDIENTE", null);

        mvc.perform(get("/api/quejas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onchainId").doesNotExist())
                .andExpect(jsonPath("$.ipfsCid").doesNotExist())
                .andExpect(jsonPath("$.contentHash").doesNotExist())
                .andExpect(jsonPath("$.historialEstados.length()").value(0))
                .andExpect(jsonPath("$.evidenciasCids.length()").value(0));
    }

    @Test
    void unaQuejaInexistenteDevuelve404YUnIdMalFormado400() throws Exception {
        mvc.perform(get("/api/quejas/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/quejas/no-es-un-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void loQueNoEsPublicoSigueCerrado() throws Exception {
        mvc.perform(get("/api/panel/quejas")).andExpect(status().isUnauthorized());
    }

    private UUID empresa(String cuit, String razonSocial) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, ?, ?)", id, cuit, razonSocial);
        return id;
    }

    /** Las confirmadas llevan cid, hash y onchain_id; las pendientes, nada de eso. */
    private UUID queja(UUID empresa, String creadaEn, String anchorStatus, Long onchainId) {
        UUID id = UUID.randomUUID();
        boolean confirmada = onchainId != null;
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, fecha_hecho,
                                   anchor_status, ipfs_cid, content_hash, onchain_id, created_at)
                VALUES (?, ?, ?, 'bancos', 'Descripción', DATE '2026-09-30', ?, ?, ?, ?, ?::timestamptz)""",
                id, usuario, empresa, anchorStatus, confirmada ? "bafy-" + id : null,
                confirmada ? hash(onchainId) : null, onchainId, creadaEn);
        return id;
    }

    private static String hash(long n) {
        return String.format("0x%064x", n);
    }
}
