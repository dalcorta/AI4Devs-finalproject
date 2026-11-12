package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.domain.AnchorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "queja")
class QuejaEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(name = "categoria_declarada", nullable = false)
    private String categoriaDeclarada;

    @Column(nullable = false)
    private String descripcion;

    @Column(name = "monto_reclamado", precision = 14, scale = 2)
    private BigDecimal montoReclamado;

    @Column(name = "fecha_hecho", nullable = false)
    private LocalDate fechaHecho;

    @Column(name = "ipfs_cid")
    private String ipfsCid;

    @Column(name = "content_hash")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_status", nullable = false)
    private AnchorStatus anchorStatus;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "queja")
    @OrderBy("createdAt ASC")
    private List<EvidenciaEntity> evidencias = new ArrayList<>();

    protected QuejaEntity() {
    }

    void actualizarAnclaje(String ipfsCid, String contentHash, AnchorStatus anchorStatus) {
        this.ipfsCid = ipfsCid;
        this.contentHash = contentHash;
        this.anchorStatus = anchorStatus;
    }

    UUID id() {
        return id;
    }

    UUID usuarioId() {
        return usuarioId;
    }

    EmpresaEntity empresa() {
        return empresa;
    }

    String categoriaDeclarada() {
        return categoriaDeclarada;
    }

    String descripcion() {
        return descripcion;
    }

    BigDecimal montoReclamado() {
        return montoReclamado;
    }

    LocalDate fechaHecho() {
        return fechaHecho;
    }

    String ipfsCid() {
        return ipfsCid;
    }

    String contentHash() {
        return contentHash;
    }

    AnchorStatus anchorStatus() {
        return anchorStatus;
    }

    Instant createdAt() {
        return createdAt;
    }

    List<EvidenciaEntity> evidencias() {
        return evidencias;
    }
}
