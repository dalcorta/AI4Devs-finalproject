package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaQuejaRepository implements QuejaRepository {

    private final QuejaJpaRepository quejas;
    private final TransaccionBlockchainJpaRepository transacciones;

    JpaQuejaRepository(QuejaJpaRepository quejas, TransaccionBlockchainJpaRepository transacciones) {
        this.quejas = quejas;
        this.transacciones = transacciones;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Queja> pendientesDeAnclaje() {
        return quejas.conAnclaje(AnchorStatus.PENDIENTE).stream().map(JpaQuejaRepository::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Queja> porContentHash(String contentHash) {
        return quejas.porContentHash(contentHash).map(JpaQuejaRepository::aDominio);
    }

    /** Persiste el avance del anclaje. El alta de quejas nuevas corresponde al caso de uso RegistrarQueja. */
    @Override
    @Transactional
    public void guardar(Queja queja) {
        QuejaEntity entidad = quejas.findById(queja.id())
                .orElseThrow(() -> new IllegalStateException("La queja " + queja.id() + " no existe"));
        entidad.actualizarAnclaje(queja.ipfsCid(), queja.contentHash(), queja.anchorStatus(), queja.onchainId());

        if (queja.txHash() == null) {
            return;
        }
        // Si el worker se cayó antes de registrar la transacción, la confirmación del listener la crea.
        TransaccionBlockchainEntity tx = transacciones.findByTxHash(queja.txHash())
                .orElseGet(() -> transacciones.save(
                        TransaccionBlockchainEntity.registroEnviado(queja.txHash(), queja.id())));
        if (queja.bloqueConfirmacion() != null) {
            tx.confirmar(queja.bloqueConfirmacion());
        }
    }

    private static Queja aDominio(QuejaEntity e) {
        return Queja.reconstituir(
                e.id(),
                e.usuarioId(),
                new Empresa(e.empresa().cuit(), e.empresa().razonSocial()),
                e.categoriaDeclarada(),
                e.descripcion(),
                e.montoReclamado(),
                e.fechaHecho(),
                e.evidencias().stream().map(EvidenciaEntity::ipfsCid).toList(),
                e.createdAt(),
                e.anchorStatus(),
                e.ipfsCid(),
                e.contentHash(),
                null,
                e.onchainId());
    }
}
