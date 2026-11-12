package ar.libroabierto.config;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.triage.application.QuejasParaTriage;
import ar.libroabierto.triage.application.TriageRepository;
import ar.libroabierto.triage.application.TriarQuejasConfirmadas;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TriageConfigTest {

    private static final String CLAVE = "sk-ant-clave-de-prueba-que-no-debe-imprimirse";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(PropiedadesDePrueba.class, TriageConfig.class)
            .withBean(QuejasParaTriage.class, () -> limite -> List.of())
            .withBean(TriageRepository.class, () -> triage -> { })
            .withPropertyValues(
                    "libroabierto.ipfs.api-url=http://127.0.0.1:5001",
                    "libroabierto.blockchain.rpc-url=http://127.0.0.1:8545",
                    "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
                    "libroabierto.blockchain.chain-id=31337",
                    "libroabierto.blockchain.relayer-private-key=59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
                    "libroabierto.worker.habilitado=true",
                    "libroabierto.worker.intervalo=PT10S",
                    "libroabierto.listener.habilitado=true",
                    "libroabierto.listener.intervalo=PT5S",
                    "libroabierto.listener.bloque-inicial=0",
                    "libroabierto.listener.confirmaciones=0",
                    "libroabierto.listener.max-bloques-por-lote=2000",
                    "libroabierto.triage.intervalo=PT10S",
                    "libroabierto.triage.modelo=claude-opus-5-5",
                    "libroabierto.triage.lote=10");

    @Test
    void conElTriageHabilitadoYSinClaveLaAplicacionNoArranca() {
        contexto.withPropertyValues("libroabierto.triage.habilitado=true", "libroabierto.triage.api-key=")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("ANTHROPIC_API_KEY"));
    }

    @Test
    void conClaveArmaElWorkerYNoExponeLaClave() {
        contexto.withPropertyValues("libroabierto.triage.habilitado=true", "libroabierto.triage.api-key=" + CLAVE)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TriarQuejasConfirmadas.class);
                    assertThat(ctx.getBean(LibroAbiertoProperties.class).toString()).doesNotContain(CLAVE);
                });
    }

    @Test
    void deshabilitadoNoNecesitaClaveNiArmaElWorker() {
        contexto.withPropertyValues("libroabierto.triage.habilitado=false", "libroabierto.triage.api-key=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(TriarQuejasConfirmadas.class);
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(LibroAbiertoProperties.class)
    static class PropiedadesDePrueba {
    }
}
