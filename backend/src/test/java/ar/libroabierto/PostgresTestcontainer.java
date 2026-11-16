package ar.libroabierto;

import ar.libroabierto.support.ModeloLocalDeTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Base de cada contexto de test. También trae el modelo local falso: ningún test con Spring depende de Ollama. */
@TestConfiguration(proxyBeanMethods = false)
@Import(ModeloLocalDeTest.class)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("docker.io/library/postgres:16.15-alpine")
                .asCompatibleSubstituteFor("postgres"));
    }
}
