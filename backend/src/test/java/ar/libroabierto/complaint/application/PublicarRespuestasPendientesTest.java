package ar.libroabierto.complaint.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.anchoring.application.AnclajeDeRespuestas;
import ar.libroabierto.complaint.domain.DatosDeRespuesta;
import ar.libroabierto.complaint.domain.DocumentoRespuesta;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicarRespuestasPendientesTest {

    private RespuestasEnMemoria respuestas;
    private IpfsFalso ipfs;
    private CadenaFalsa cadena;
    private PublicarRespuestasPendientes worker;

    @BeforeEach
    void setUp() {
        respuestas = new RespuestasEnMemoria();
        ipfs = new IpfsFalso();
        cadena = new CadenaFalsa();
        worker = new PublicarRespuestasPendientes(respuestas, ipfs, cadena);
    }

    @Test
    void publicaElDocumentoGuardaSuHashAntesDeEnviarYDespuesLaTransaccion() {
        UUID id = respuestas.agregar(datos(7));

        PublicarRespuestasPendientes.Resultado resultado = worker.ejecutar();

        assertThat(resultado).isEqualTo(new PublicarRespuestasPendientes.Resultado(1, 0));
        String documento = ipfs.publicados.get("bafy-1");
        assertThat(documento).isEqualTo(DocumentoRespuesta.json(datos(7)));
        assertThat(respuestas.eventos).containsExactly(
                "publicada " + id + " bafy-1 " + CanonicalHasher.hash(documento),
                "enviada " + id + " 0xtx-7");
        assertThat(cadena.envios).containsExactly(new Envio(7, CanonicalHasher.hash(documento), "bafy-1"));
    }

    @Test
    void siFallaLaCadenaAlReintentarNoRepublicaEnIpfs() {
        UUID id = respuestas.agregar(datos(7));
        cadena.fallar = true;

        assertThat(worker.ejecutar()).isEqualTo(new PublicarRespuestasPendientes.Resultado(0, 1));
        assertThat(respuestas.pendientes.get(id).contentHash()).isNotNull();

        cadena.fallar = false;
        assertThat(worker.ejecutar()).isEqualTo(new PublicarRespuestasPendientes.Resultado(1, 0));
        assertThat(ipfs.publicados).hasSize(1);
        assertThat(cadena.envios).hasSize(1);
    }

    @Test
    void unaRespuestaQueFallaNoFrenaALasDemas() {
        respuestas.agregar(datos(7));
        UUID segunda = respuestas.agregar(datos(8));
        cadena.fallarCon = 7L;

        assertThat(worker.ejecutar()).isEqualTo(new PublicarRespuestasPendientes.Resultado(1, 1));
        assertThat(respuestas.eventos).contains("enviada " + segunda + " 0xtx-8");
    }

    private static DatosDeRespuesta datos(long onchainId) {
        return new DatosDeRespuesta(onchainId, String.format("0x%064x", onchainId),
                new Empresa("30712345671", "Empresa S.A."), "Respuesta a la queja " + onchainId + ".",
                Instant.parse("2026-11-14T15:00:00Z"));
    }

    record Envio(long quejaOnchainId, String contentHash, String ipfsCid) {
    }

    static class RespuestasEnMemoria extends ResponderQuejaTest.RespuestasEnMemoria {
        final Map<UUID, RespuestaPendiente> pendientes = new LinkedHashMap<>();
        final List<String> eventos = new ArrayList<>();

        UUID agregar(DatosDeRespuesta datos) {
            UUID id = UUID.randomUUID();
            pendientes.put(id, new RespuestaPendiente(id, datos, null, null));
            return id;
        }

        @Override
        public List<RespuestaPendiente> pendientesDeAnclaje() {
            return List.copyOf(pendientes.values());
        }

        @Override
        public void registrarPublicacion(UUID respuestaId, String ipfsCid, String contentHash) {
            RespuestaPendiente actual = pendientes.get(respuestaId);
            pendientes.put(respuestaId, new RespuestaPendiente(respuestaId, actual.datos(), ipfsCid, contentHash));
            eventos.add("publicada " + respuestaId + " " + ipfsCid + " " + contentHash);
        }

        @Override
        public void marcarEnviada(UUID respuestaId, String txHash) {
            pendientes.remove(respuestaId);
            eventos.add("enviada " + respuestaId + " " + txHash);
        }

        @Override
        public Optional<UUID> empresaDe(UUID usuarioId) {
            return Optional.empty();
        }
    }

    static class IpfsFalso implements IpfsGateway {
        final Map<String, String> publicados = new LinkedHashMap<>();

        @Override
        public String publicar(String json) {
            String cid = "bafy-" + (publicados.size() + 1);
            publicados.put(cid, json);
            return cid;
        }
    }

    static class CadenaFalsa implements AnclajeDeRespuestas {
        final List<Envio> envios = new ArrayList<>();
        boolean fallar;
        Long fallarCon;

        @Override
        public String registrarRespuesta(long quejaOnchainId, String contentHash, String ipfsCid) {
            if (fallar || Long.valueOf(quejaOnchainId).equals(fallarCon)) {
                throw new IllegalStateException("Nodo caído");
            }
            envios.add(new Envio(quejaOnchainId, contentHash, ipfsCid));
            return "0xtx-" + quejaOnchainId;
        }
    }
}
