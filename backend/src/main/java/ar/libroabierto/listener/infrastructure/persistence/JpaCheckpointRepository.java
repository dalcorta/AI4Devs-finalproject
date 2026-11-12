package ar.libroabierto.listener.infrastructure.persistence;

import ar.libroabierto.listener.application.CheckpointRepository;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.transaction.annotation.Transactional;

/** Checkpoint por contrato: si se despliega un contrato nuevo, su listener arranca de cero sin pisar al anterior. */
public class JpaCheckpointRepository implements CheckpointRepository {

    private final ListenerCheckpointJpaRepository checkpoints;
    private final String contrato;

    JpaCheckpointRepository(ListenerCheckpointJpaRepository checkpoints, String contrato) {
        this.checkpoints = checkpoints;
        // Las direcciones llegan con o sin checksum EIP-55; en la base se guardan siempre en minúsculas.
        this.contrato = contrato.toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalLong ultimoBloque() {
        return checkpoints.findById(contrato)
                .map(c -> OptionalLong.of(c.ultimoBloque()))
                .orElse(OptionalLong.empty());
    }

    @Override
    @Transactional
    public void guardar(long bloque) {
        ListenerCheckpointEntity checkpoint = checkpoints.findById(contrato)
                .orElseGet(() -> new ListenerCheckpointEntity(contrato));
        checkpoint.avanzarA(bloque);
        checkpoints.save(checkpoint);
    }
}
