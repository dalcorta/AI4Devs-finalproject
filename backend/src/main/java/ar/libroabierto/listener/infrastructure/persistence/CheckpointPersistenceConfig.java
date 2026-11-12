package ar.libroabierto.listener.infrastructure.persistence;

import ar.libroabierto.config.LibroAbiertoProperties;
import ar.libroabierto.listener.application.CheckpointRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CheckpointPersistenceConfig {

    @Bean
    CheckpointRepository checkpointRepository(ListenerCheckpointJpaRepository checkpoints,
            LibroAbiertoProperties props) {
        return new JpaCheckpointRepository(checkpoints, props.blockchain().contrato());
    }
}
