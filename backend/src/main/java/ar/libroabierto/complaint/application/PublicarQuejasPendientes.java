package ar.libroabierto.complaint.application;

import ar.libroabierto.anchoring.application.BlockchainGateway;
import ar.libroabierto.complaint.domain.DocumentoPublico;
import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.shared.hashing.CanonicalHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker del patrón outbox: toma las quejas PENDIENTE, las publica en IPFS y envía su anclaje on-chain.
 * Una queja que falla queda PENDIENTE y se reintenta en la próxima ejecución.
 */
public class PublicarQuejasPendientes {

    private static final Logger log = LoggerFactory.getLogger(PublicarQuejasPendientes.class);

    private final QuejaRepository repositorio;
    private final IpfsGateway ipfs;
    private final BlockchainGateway cadena;

    public PublicarQuejasPendientes(QuejaRepository repositorio, IpfsGateway ipfs, BlockchainGateway cadena) {
        this.repositorio = repositorio;
        this.ipfs = ipfs;
        this.cadena = cadena;
    }

    public Resultado ejecutar() {
        int enviadas = 0;
        int fallidas = 0;
        for (Queja queja : repositorio.pendientesDeAnclaje()) {
            try {
                publicarYAnclar(queja);
                enviadas++;
            } catch (RuntimeException e) {
                fallidas++;
                log.warn("No se pudo anclar la queja {}; se reintentará", queja.id(), e);
            }
        }
        return new Resultado(enviadas, fallidas);
    }

    private void publicarYAnclar(Queja queja) {
        if (!queja.estaPublicada()) {
            String documento = DocumentoPublico.json(queja);
            String cid = ipfs.publicar(documento);
            queja.registrarPublicacion(cid, CanonicalHasher.hash(documento));
            // El listener encuentra la queja por content_hash: tiene que estar persistido antes de que exista el evento.
            repositorio.guardar(queja);
        }
        String txHash = cadena.registrarQueja(
                Long.parseLong(queja.empresa().cuit()), queja.contentHash(), queja.ipfsCid());
        queja.marcarEnviada(txHash);
        repositorio.guardar(queja);
    }

    public record Resultado(int enviadas, int fallidas) {
    }
}
