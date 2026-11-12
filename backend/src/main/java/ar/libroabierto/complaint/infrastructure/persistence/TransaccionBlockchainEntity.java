package ar.libroabierto.complaint.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaccion_blockchain")
class TransaccionBlockchainEntity {

    @Id
    private UUID id;

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "referencia_id", nullable = false)
    private UUID referenciaId;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TransaccionBlockchainEntity() {
    }

    static TransaccionBlockchainEntity registroEnviado(String txHash, UUID quejaId) {
        TransaccionBlockchainEntity tx = new TransaccionBlockchainEntity();
        tx.id = UUID.randomUUID();
        tx.txHash = txHash;
        tx.tipo = "REGISTRO";
        tx.referenciaId = quejaId;
        tx.estado = "ENVIADA";
        tx.intentos = 1;
        return tx;
    }
}
