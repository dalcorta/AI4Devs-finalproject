package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import java.util.List;
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

    /** Persiste el avance del anclaje. El alta de quejas nuevas corresponde al caso de uso RegistrarQueja. */
    @Override
    @Transactional
    public void guardar(Queja queja) {
        QuejaEntity entidad = quejas.findById(queja.id())
                .orElseThrow(() -> new IllegalStateException("La queja " + queja.id() + " no existe"));
        entidad.actualizarAnclaje(queja.ipfsCid(), queja.contentHash(), queja.anchorStatus());

        if (queja.txHash() != null && !transacciones.existsByTxHash(queja.txHash())) {
            transacciones.save(TransaccionBlockchainEntity.registroEnviado(queja.txHash(), queja.id()));
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
                null);
    }
}
