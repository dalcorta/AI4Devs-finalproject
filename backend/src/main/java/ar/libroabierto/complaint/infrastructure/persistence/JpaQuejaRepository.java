package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.application.EmpresaRepository;
import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.application.RegistroDeQuejas;
import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import ar.libroabierto.triage.application.QuejasParaTriage;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaQuejaRepository implements QuejaRepository, QuejasParaTriage, RegistroDeQuejas, EmpresaRepository {

    private final QuejaJpaRepository quejas;
    private final EmpresaJpaRepository empresas;
    private final TransaccionBlockchainJpaRepository transacciones;

    JpaQuejaRepository(QuejaJpaRepository quejas, EmpresaJpaRepository empresas,
            TransaccionBlockchainJpaRepository transacciones) {
        this.quejas = quejas;
        this.empresas = empresas;
        this.transacciones = transacciones;
    }

    @Override
    @Transactional
    public void registrar(Queja queja) {
        Empresa empresa = queja.empresa();
        EmpresaEntity entidadEmpresa = empresas.findByCuit(empresa.cuit())
                .orElseGet(() -> empresas.save(EmpresaEntity.nueva(empresa.cuit(), empresa.razonSocial())));
        quejas.save(QuejaEntity.nueva(queja.id(), queja.usuarioId(), entidadEmpresa, queja.categoria(),
                queja.descripcion(), queja.montoReclamado(), queja.fechaHecho(), queja.anchorStatus(),
                queja.registradaEn()));
    }

    @Override
    @Transactional(readOnly = true)
    public long registradasDesde(UUID usuarioId, Instant desde) {
        return quejas.countByUsuarioIdAndCreatedAtGreaterThanEqual(usuarioId, desde);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Empresa> porCuit(String cuit) {
        return empresas.findByCuit(cuit).map(e -> new Empresa(e.cuit(), e.razonSocial()));
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

    @Override
    @Transactional(readOnly = true)
    public List<Queja> confirmadasSinTriage(int limite) {
        List<UUID> ids = quejas.idsConfirmadasSinTriage(limite);
        if (ids.isEmpty()) {
            return List.of();
        }
        return quejas.conIds(ids).stream()
                .sorted(Comparator.comparingInt(e -> ids.indexOf(e.id())))
                .map(JpaQuejaRepository::aDominio)
                .toList();
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
