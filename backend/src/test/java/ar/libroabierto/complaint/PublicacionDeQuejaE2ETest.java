package ar.libroabierto.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.PostgresTestcontainer;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import ar.libroabierto.support.AnvilLocal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Criterio de aceptación del ticket de publicación y anclaje, con la aplicación completa: una queja PENDIENTE
 * termina ENVIADA por el worker programado, publicada en IPFS y anclada en el contrato con el mismo hash.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestcontainer.class)
class PublicacionDeQuejaE2ETest {

    @Container
    static final GenericContainer<?> KUBO = new GenericContainer<>(DockerImageName.parse("docker.io/ipfs/kubo:v0.43.1"))
            .withExposedPorts(5001)
            .waitingFor(Wait.forLogMessage(".*Daemon is ready.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static AnvilLocal anvil;
    private static String contrato;

    @Autowired
    private JdbcTemplate jdbc;

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
    }

    @AfterAll
    static void apagarAnvil() {
        if (anvil != null) {
            anvil.close();
        }
    }

    @Test
    void unaQuejaPendienteQuedaPublicadaYAncladaConElMismoHash() throws Exception {
        UUID queja = quejaPendiente();

        Map<String, Object> fila = esperarEstado(queja, "ENVIADA");

        String contentHash = (String) fila.get("content_hash");
        String cid = (String) fila.get("ipfs_cid");
        String txHash = jdbc.queryForObject(
                "SELECT tx_hash FROM transaccion_blockchain WHERE tipo = 'REGISTRO' AND referencia_id = ?",
                String.class, queja);

        assertThat(anvil.esperarRecibo(txHash).isStatusOK()).isTrue();
        assertThat(anvil.idPorHash(contrato, contentHash)).isEqualTo(BigInteger.ONE);

        String documento = leerDeIpfs(cid);
        assertThat(CanonicalHasher.hash(documento)).isEqualTo(contentHash);
        assertThat(documento)
                .contains("30712345678")
                .doesNotContain("Persona Ficticia Única", "denunciante-e2e@example.test", "1155443322");
    }

    private UUID quejaPendiente() {
        UUID usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, dni_cifrado, telefono_cifrado)
                VALUES (?, 'denunciante-e2e@example.test', 'x', 'CIUDADANO', 'Persona Ficticia Única',
                        '\\x01'::bytea, convert_to('1155443322', 'UTF8'))""", usuario);
        UUID empresa = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, cuit, razon_social) VALUES (?, '30712345678', 'Empresa Ficticia S.A.')",
                empresa);
        UUID queja = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO queja (id, usuario_id, empresa_id, categoria_declarada, descripcion, monto_reclamado,
                                   fecha_hecho)
                VALUES (?, ?, ?, 'telecomunicaciones', 'Corte de servicio de internet durante 15 días.', 15000,
                        DATE '2026-10-15')""", queja, usuario, empresa);
        return queja;
    }

    private Map<String, Object> esperarEstado(UUID queja, String estado) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(limite)) {
            Map<String, Object> fila = jdbc.queryForMap(
                    "SELECT anchor_status, content_hash, ipfs_cid FROM queja WHERE id = ?", queja);
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
}
