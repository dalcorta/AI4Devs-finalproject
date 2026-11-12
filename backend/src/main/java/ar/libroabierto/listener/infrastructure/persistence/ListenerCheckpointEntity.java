package ar.libroabierto.listener.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "listener_checkpoint")
class ListenerCheckpointEntity {

    @Id
    private String contrato;

    @Column(name = "ultimo_bloque", nullable = false)
    private long ultimoBloque;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ListenerCheckpointEntity() {
    }

    ListenerCheckpointEntity(String contrato) {
        this.contrato = contrato;
    }

    void avanzarA(long bloque) {
        this.ultimoBloque = bloque;
        this.updatedAt = Instant.now();
    }

    long ultimoBloque() {
        return ultimoBloque;
    }
}
