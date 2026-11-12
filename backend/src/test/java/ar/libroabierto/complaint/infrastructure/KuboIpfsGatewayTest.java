package ar.libroabierto.complaint.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Sin disabledWithoutDocker: si falta el motor de contenedores, el test falla en vez de omitirse en silencio.
@Testcontainers
class KuboIpfsGatewayTest {

    @Container
    static final GenericContainer<?> KUBO = new GenericContainer<>(DockerImageName.parse("docker.io/ipfs/kubo:v0.43.1"))
            .withExposedPorts(5001)
            .waitingFor(Wait.forLogMessage(".*Daemon is ready.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static final String DOCUMENTO =
            "{\"empresa\":{\"cuit\":\"30712345678\",\"razon_social\":\"Compañía Ñandú S.A.\"},\"schema_version\":\"1.0\"}";

    private KuboIpfsGateway gateway() {
        return new KuboIpfsGateway(apiUrl());
    }

    @Test
    void publicaYElContenidoRecuperadoEsIdenticoByteAByte() throws Exception {
        String cid = gateway().publicar(DOCUMENTO);

        assertThat(cid).startsWith("baf");
        assertThat(leer(cid)).isEqualTo(DOCUMENTO.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void publicarElMismoContenidoDosVecesDaElMismoCid() {
        assertThat(gateway().publicar(DOCUMENTO)).isEqualTo(gateway().publicar(DOCUMENTO));
    }

    @Test
    void siKuboNoRespondeFallaConExcepcion() {
        KuboIpfsGateway caido = new KuboIpfsGateway("http://127.0.0.1:1");

        assertThatThrownBy(() -> caido.publicar(DOCUMENTO)).isInstanceOf(IllegalStateException.class);
    }

    private static String apiUrl() {
        return "http://" + KUBO.getHost() + ":" + KUBO.getMappedPort(5001);
    }

    private static byte[] leer(String cid) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(apiUrl() + "/api/v0/cat?arg=" + cid))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.ofByteArray()).body();
    }
}
