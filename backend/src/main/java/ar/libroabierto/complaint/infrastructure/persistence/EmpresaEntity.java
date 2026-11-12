package ar.libroabierto.complaint.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "empresa")
class EmpresaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String cuit;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected EmpresaEntity() {
    }

    static EmpresaEntity nueva(String cuit, String razonSocial) {
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.id = UUID.randomUUID();
        empresa.cuit = cuit;
        empresa.razonSocial = razonSocial;
        return empresa;
    }

    String cuit() {
        return cuit;
    }

    String razonSocial() {
        return razonSocial;
    }
}
