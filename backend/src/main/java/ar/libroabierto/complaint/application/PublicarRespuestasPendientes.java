package ar.libroabierto.complaint.application;

import ar.libroabierto.anchoring.application.AnclajeDeRespuestas;
import ar.libroabierto.complaint.domain.DocumentoRespuesta;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker outbox de las respuestas: las publica en IPFS y envía su anclaje. Una respuesta que falla queda pendiente y se
 * reintenta en la próxima ejecución, sin republicar si ya tiene hash.
 */
public class PublicarRespuestasPendientes {

    private static final Logger log = LoggerFactory.getLogger(PublicarRespuestasPendientes.class);

    private final Respuestas respuestas;
    private final IpfsGateway ipfs;
    private final AnclajeDeRespuestas cadena;

    public PublicarRespuestasPendientes(Respuestas respuestas, IpfsGateway ipfs, AnclajeDeRespuestas cadena) {
        this.respuestas = respuestas;
        this.ipfs = ipfs;
        this.cadena = cadena;
    }

    public Resultado ejecutar() {
        int enviadas = 0;
        int fallidas = 0;
        for (RespuestaPendiente respuesta : respuestas.pendientesDeAnclaje()) {
            try {
                publicarYAnclar(respuesta);
                enviadas++;
            } catch (RuntimeException e) {
                fallidas++;
                log.warn("No se pudo anclar la respuesta {}; se reintentará", respuesta.id(), e);
            }
        }
        return new Resultado(enviadas, fallidas);
    }

    private void publicarYAnclar(RespuestaPendiente respuesta) {
        String cid = respuesta.ipfsCid();
        String hash = respuesta.contentHash();
        if (!respuesta.estaPublicada()) {
            String documento = DocumentoRespuesta.json(respuesta.datos());
            cid = ipfs.publicar(documento);
            hash = CanonicalHasher.hash(documento);
            respuestas.registrarPublicacion(respuesta.id(), cid, hash);
        }
        respuestas.marcarEnviada(respuesta.id(),
                cadena.registrarRespuesta(respuesta.datos().quejaOnchainId(), hash, cid));
    }

    public record Resultado(int enviadas, int fallidas) {
    }
}
