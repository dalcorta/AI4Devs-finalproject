package ar.libroabierto.complaint.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.anchoring.application.BlockchainGateway;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicarQuejasPendientesTest {

    private RepositorioEnMemoria repositorio;
    private IpfsFalso ipfs;
    private CadenaFalsa cadena;
    private PublicarQuejasPendientes worker;

    @BeforeEach
    void setUp() {
        repositorio = new RepositorioEnMemoria();
        ipfs = new IpfsFalso();
        cadena = new CadenaFalsa();
        worker = new PublicarQuejasPendientes(repositorio, ipfs, cadena);
    }

    @Test
    void publicaEnIpfsGuardaElHashYEnviaLaTransaccion() {
        Queja queja = repositorio.agregar(queja("30712345678"));

        PublicarQuejasPendientes.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new PublicarQuejasPendientes.Resultado(1, 0));
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.ENVIADA);
        assertThat(queja.ipfsCid()).isEqualTo("bafy-1");
        assertThat(queja.contentHash()).isEqualTo(CanonicalHasher.hash(ipfs.publicados.get("bafy-1")));
        assertThat(queja.txHash()).isEqualTo("0xtx-1");
        assertThat(cadena.envios).containsExactly(new Envio(30712345678L, queja.contentHash(), "bafy-1"));
    }

    @Test
    void siFallaIpfsLaQuejaQuedaPendienteSinHashNiTransaccion() {
        Queja queja = repositorio.agregar(queja("30712345678"));
        ipfs.fallar = true;

        PublicarQuejasPendientes.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new PublicarQuejasPendientes.Resultado(0, 1));
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.PENDIENTE);
        assertThat(queja.contentHash()).isNull();
        assertThat(cadena.envios).isEmpty();
    }

    @Test
    void siFallaLaCadenaConservaElHashYAlReintentarNoRepublicaEnIpfs() {
        Queja queja = repositorio.agregar(queja("30712345678"));
        cadena.fallar = true;

        worker.ejecutar();

        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.PENDIENTE);
        assertThat(queja.contentHash()).isNotNull();
        String hashGuardado = queja.contentHash();

        cadena.fallar = false;
        PublicarQuejasPendientes.Resultado reintento = worker.ejecutar();

        assertThat(reintento).isEqualTo(new PublicarQuejasPendientes.Resultado(1, 0));
        assertThat(queja.anchorStatus()).isEqualTo(AnchorStatus.ENVIADA);
        assertThat(queja.contentHash()).isEqualTo(hashGuardado);
        assertThat(ipfs.publicados).hasSize(1);
    }

    @Test
    void elHashSePersisteAntesDeEnviarLaTransaccion() {
        Queja queja = repositorio.agregar(queja("30712345678"));
        cadena.alEnviar = () -> assertThat(repositorio.hashPersistido(queja.id())).isEqualTo(queja.contentHash());

        worker.ejecutar();

        assertThat(cadena.envios).hasSize(1);
    }

    @Test
    void unaQuejaQueFallaNoFrenaALasDemas() {
        Queja falla = repositorio.agregar(queja("30712345678"));
        Queja sale = repositorio.agregar(queja("30500010912"));
        cadena.cuitQueFalla = 30712345678L;

        PublicarQuejasPendientes.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new PublicarQuejasPendientes.Resultado(1, 1));
        assertThat(falla.anchorStatus()).isEqualTo(AnchorStatus.PENDIENTE);
        assertThat(sale.anchorStatus()).isEqualTo(AnchorStatus.ENVIADA);
    }

    @Test
    void noVuelveAProcesarQuejasYaEnviadas() {
        repositorio.agregar(queja("30712345678"));
        worker.ejecutar();

        PublicarQuejasPendientes.Resultado segunda = worker.ejecutar();

        assertThat(segunda).isEqualTo(new PublicarQuejasPendientes.Resultado(0, 0));
        assertThat(cadena.envios).hasSize(1);
    }

    private static Queja queja(String cuit) {
        return Queja.nueva(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Empresa(cuit, "Empresa " + cuit),
                "telecomunicaciones",
                "Descripción de prueba para " + cuit,
                new BigDecimal("1000"),
                LocalDate.parse("2026-10-15"),
                List.of(),
                Instant.parse("2026-10-18T12:00:00Z"));
    }

    record Envio(long cuit, String contentHash, String ipfsCid) {
    }

    static class RepositorioEnMemoria implements QuejaRepository {
        private final Map<UUID, Queja> quejas = new LinkedHashMap<>();
        private final Map<UUID, String> hashes = new LinkedHashMap<>();

        Queja agregar(Queja queja) {
            quejas.put(queja.id(), queja);
            return queja;
        }

        String hashPersistido(UUID id) {
            return hashes.get(id);
        }

        @Override
        public List<Queja> pendientesDeAnclaje() {
            return quejas.values().stream().filter(q -> q.anchorStatus() == AnchorStatus.PENDIENTE).toList();
        }

        @Override
        public Optional<Queja> porContentHash(String contentHash) {
            return quejas.values().stream().filter(q -> contentHash.equals(q.contentHash())).findFirst();
        }

        @Override
        public void guardar(Queja queja) {
            quejas.put(queja.id(), queja);
            hashes.put(queja.id(), queja.contentHash());
        }
    }

    static class IpfsFalso implements IpfsGateway {
        final Map<String, String> publicados = new LinkedHashMap<>();
        boolean fallar;

        @Override
        public String publicar(String json) {
            if (fallar) {
                throw new IllegalStateException("IPFS no disponible");
            }
            String cid = "bafy-" + (publicados.size() + 1);
            publicados.put(cid, json);
            return cid;
        }
    }

    static class CadenaFalsa implements BlockchainGateway {
        final List<Envio> envios = new ArrayList<>();
        boolean fallar;
        Long cuitQueFalla;
        Runnable alEnviar = () -> { };

        @Override
        public String registrarQueja(long companyCuit, String contentHash, String ipfsCid) {
            if (fallar || Long.valueOf(companyCuit).equals(cuitQueFalla)) {
                throw new IllegalStateException("RPC no disponible");
            }
            alEnviar.run();
            envios.add(new Envio(companyCuit, contentHash, ipfsCid));
            return "0xtx-" + envios.size();
        }
    }
}
