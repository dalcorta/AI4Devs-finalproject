package ar.libroabierto.triage.infrastructure.persistence;

import ar.libroabierto.triage.domain.EstadoTriage;
import ar.libroabierto.triage.domain.Prioridad;
import ar.libroabierto.triage.domain.Triage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "triage")
class TriageEntity {

    @Id
    private UUID id;

    @Column(name = "queja_id", nullable = false, unique = true)
    private UUID quejaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTriage estado;

    @Column(name = "categoria_sugerida")
    private String categoriaSugerida;

    private String resumen;

    @Enumerated(EnumType.STRING)
    private Prioridad prioridad;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "articulos_sugeridos")
    private List<String> articulosSugeridos;

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TriageEntity() {
    }

    static TriageEntity de(Triage triage) {
        TriageEntity entidad = new TriageEntity();
        entidad.id = UUID.randomUUID();
        entidad.quejaId = triage.quejaId();
        entidad.estado = triage.estado();
        entidad.categoriaSugerida = triage.categoriaSugerida();
        entidad.resumen = triage.resumen();
        entidad.prioridad = triage.prioridad();
        entidad.articulosSugeridos = triage.estado() == EstadoTriage.MANUAL ? null : triage.articulos();
        entidad.modelo = triage.modelo();
        entidad.intentos = triage.intentos();
        return entidad;
    }
}
