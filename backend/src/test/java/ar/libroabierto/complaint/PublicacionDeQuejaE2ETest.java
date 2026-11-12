package ar.libroabierto.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.listener.application.CheckpointRepository;
import ar.libroabierto.listener.application.FuenteDeEventos;
import ar.libroabierto.listener.application.ProcesarEventosOnChain;
import ar.libroabierto.triage.application.LlmClient;
import ar.libroabierto.triage.application.RespuestaLlm;
import ar.libroabierto.triage.application.SolicitudLlm;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import ar.libroabierto.support.AnvilLocal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Criterios de aceptación de los tickets de publicación, listener y triage, con la aplicación completa: una queja
 * PENDIENTE queda publicada en IPFS y anclada por el worker, el listener la confirma al leer el evento ComplaintFiled
 * real y el worker de triage la clasifica con el LLM (simulado) viendo solo su contenido público. Reprocesar los
 * mismos eventos no reescribe la queja ni genera un segundo triage.
 */
@SpringBootTest
@Testcontainers
@Import({PostgresTestcontainer.class, PublicacionDeQuejaE2ETest.LlmFalso.class})
class PublicacionDeQuejaE2ETest {

    @Container
    static final GenericContainer<?> KUBO = new GenericContainer<>(DockerImageName.parse("docker.io/ipfs/kubo:v0.43.1"))
            .withExposedPorts(5001)
            .waitingFor(Wait.forLogMessage(".*Daemon is ready.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static AnvilLocal anvil;
    private static String contrato;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LlmFalso llm;

    @Autowired
    private QuejaRepository quejas;

    @Autowired
    private FuenteDeEventos fuente;

    @DynamicPropertySource
    static void infraestructura(DynamicPropertyRegistry props) throws Exception {
        anvil = AnvilLocal.iniciar();
        contrato = anvil.desplegarContrato();

        props.add("libroabierto.ipfs.api-url", () -> "http://" + KUBO.getHost() + ":" + KUBO.getMappedPort(5001));
        props.add("libroabierto.blockchain.rpc-url", anvil::rpcUrl);
        props.add("libroabierto.blockchain.contrato", () -> contrato);
        props.add("libroabierto.blockchain.chain-id", () -> AnvilLocal.CHAIN_ID);
        props.add("libroabierto.blockchain.relayer-private-key",
                () -> AnvilLocal.RELAYER.getEcKeyPair().getPrivateKey().toString(16));
        props.add("libroabierto.worker.intervalo", () -> "PT0.5S");
        props.add("libroabierto.listener.intervalo", () -> "PT0.5S");
        props.add("libroabierto.triage.intervalo", () -> "PT0.5S");
        // El LLM real se reemplaza por LlmFalso; la clave solo cumple con la configuración obligatoria.
        props.add("libroabierto.triage.api-key", () -> "clave-no-usada");
    }

    @AfterAll
    static void apagarAnvil() {
        if (anvil != null) {
            anvil.close();
        }
    }

    @Test
    void unaQuejaPendienteQuedaPublicadaAncladaConfirmadaYTriada() throws Exception {
        UUID queja = quejaPendiente("30712345678", "Corte de servicio de internet durante 15 días.");

        Map<String, Object> fila = esperarEstado(queja, "CONFIRMADA");

        String contentHash = (String) fila.get("content_hash");
        String cid = (String) fila.get("ipfs_cid");
        String txHash = jdbc.queryForObject(
                "SELECT tx_hash FROM transaccion_blockchain WHERE tipo = 'REGISTRO' AND referencia_id = ?",
                String.class, queja);

        var recibo = anvil.esperarRecibo(txHash);
        assertThat(recibo.isStatusOK()).isTrue();
        BigInteger idOnChain = anvil.idPorHash(contrato, contentHash);
        assertThat(idOnChain).isPositive();
        assertThat(((Number) fila.get("onchain_id")).longValue()).isEqualTo(idOnChain.longValueExact());
        assertThat(jdbc.queryForMap("SELECT estado, block_number FROM transaccion_blockchain WHERE tx_hash = ?", txHash))
                .containsEntry("estado", "CONFIRMADA")
                .containsEntry("block_number", recibo.getBlockNumber().longValueExact());
        Map<String, Object> triage = esperarTriage(queja);
        assertThat(triage).containsEntry("estado", "SUGERIDO").containsEntry("prioridad", "MEDIA")
                .containsEntry("modelo", "llm-falso");
        // El LLM recibió exactamente el documento público, sin datos personales del denunciante.
        assertThat(llm.contenidosCon("Corte de servicio de internet")).singleElement().asString()
                .contains("30712345678")
                .doesNotContain("Persona Ficticia Única", "@example.test", "1155443322");

        String documento = leerDeIpfs(cid);
        assertThat(CanonicalHasher.hash(documento)).isEqualTo(contentHash);
        assertThat(documento)
                .contains("30712345678")
                .doesNotContain("Persona Ficticia Única", "@example.test", "1155443322");
    }

    @Test
    void reprocesarLosEventosDesdeCeroNoReescribeLaQuejaNiGeneraOtroTriage() throws Exception {
        UUID queja = quejaPendiente("30500010912", "Cobro duplicado en la factura de octubre.");
        Map<String, Object> confirmada = esperarEstado(queja, "CONFIRMADA");

        // Un listener sin checkpoint (como tras perderlo en un reinicio) vuelve a leer todos los eventos reales.
        var sinCheckpoint = new CheckpointRepository() {
            private OptionalLong valor = OptionalLong.empty();

            @Override
            public OptionalLong ultimoBloque() {
                return valor;
            }

            @Override
            public void guardar(long bloque) {
                valor = OptionalLong.of(bloque);
            }
        };
        ProcesarEventosOnChain.Resultado replay = new ProcesarEventosOnChain(
                quejas, fuente, sinCheckpoint, 0, 0, 1000).ejecutar();

        assertThat(replay.confirmadas()).isZero();
        assertThat(replay.ignorados()).isPositive();
        assertThat(jdbc.queryForMap("SELECT anchor_status, content_hash, ipfs_cid, onchain_id FROM queja WHERE id = ?",
                queja)).isEqualTo(confirmada);
        esperarTriage(queja);
        Thread.sleep(1500); // varias pasadas del worker de triage después del reproceso
        assertThat(jdbc.queryForObject("SELECT count(*) FROM triage WHERE queja_id = ?", Long.class, queja))
                .isEqualTo(1);
        assertThat(llm.contenidosCon("Cobro duplicado en la factura")).hasSize(1);
    }

    private UUID quejaPendiente(String cuit, String descripcion) {
        UUID usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, dni_cifrado, telefono_cifrado)
                VALUES (?, ?, 'x', 'CIUDADANO', 'Persona Ficticia Única',
                        '\\x01'::bytea, convert_to('1155443322', 'UTF8'))""",
                usuario, "denunciante-" + usuario + "@example.test");
        UUID empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, ?, 'Empresa Ficticia S.A.')",
                empresa, cuit);
        UUID queja = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, monto_reclamado,
                                   fecha_hecho)
                VALUES (?, ?, ?, 'telecomunicaciones', ?, 15000, DATE '2026-10-15')""",
                queja, usuario, empresa, descripcion);
        return queja;
    }

    private Map<String, Object> esperarTriage(UUID queja) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(limite)) {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT estado, prioridad, modelo FROM triage WHERE queja_id = ?", queja);
            if (!filas.isEmpty()) {
                return filas.get(0);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("La queja " + queja + " no fue triada a tiempo");
    }

    private Map<String, Object> esperarEstado(UUID queja, String estado) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(limite)) {
            Map<String, Object> fila = jdbc.queryForMap(
                    "SELECT anchor_status, content_hash, ipfs_cid, onchain_id FROM queja WHERE id = ?", queja);
            if (estado.equals(fila.get("anchor_status"))) {
                return fila;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("La queja " + queja + " no llegó a " + estado + " a tiempo");
    }

    private static String leerDeIpfs(String cid) throws Exception {
        String api = "http://" + KUBO.getHost() + ":" + KUBO.getMappedPort(5001);
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(api + "/api/v0/cat?arg=" + cid))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Reemplaza al cliente de Anthropic: responde un triage válido y registra el contenido de cada pedido. */
    @TestConfiguration(proxyBeanMethods = false)
    static class LlmFalso implements LlmClient {
        private final List<String> contenidos = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        LlmClient llmFalso() {
            return this;
        }

        @Override
        public RespuestaLlm completar(SolicitudLlm solicitud) {
            contenidos.add(solicitud.contenido());
            return new RespuestaLlm("""
                    {"categoria": "telecomunicaciones", "resumen": "Queja de prueba.", "prioridad": "MEDIA",
                     "articulos": ["19"]}""", "llm-falso");
        }

        List<String> contenidosCon(String texto) {
            return contenidos.stream().filter(c -> c.contains(texto)).toList();
        }
    }
}
