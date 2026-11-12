package ar.libroabierto.config;

import static org.assertj.core.api.Assertions.assertThat;

import ar.libroabierto.complaint.application.PublicarQuejasPendientes;
import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.listener.application.CheckpointRepository;
import ar.libroabierto.listener.application.ProcesarEventosOnChain;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AnclajeConfigTest {

    private static final String CLAVE = "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(AnclajeConfig.class)
            .withBean(QuejaRepository.class, () -> new QuejaRepository() {
                @Override
                public List<ar.libroabierto.complaint.domain.Queja> pendientesDeAnclaje() {
                    return List.of();
                }

                @Override
                public java.util.Optional<ar.libroabierto.complaint.domain.Queja> porContentHash(String hash) {
                    return java.util.Optional.empty();
                }

                @Override
                public void guardar(ar.libroabierto.complaint.domain.Queja queja) {
                }
            })
            .withBean(CheckpointRepository.class, () -> new CheckpointRepository() {
                @Override
                public OptionalLong ultimoBloque() {
                    return OptionalLong.empty();
                }

                @Override
                public void guardar(long bloque) {
                }
            })
            .withPropertyValues(
                    "libroabierto.ipfs.api-url=http://127.0.0.1:5001",
                    "libroabierto.blockchain.rpc-url=http://127.0.0.1:8545",
                    "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
                    "libroabierto.blockchain.chain-id=31337",
                    "libroabierto.worker.habilitado=true",
                    "libroabierto.worker.intervalo=PT10S",
                    "libroabierto.listener.habilitado=true",
                    "libroabierto.listener.intervalo=PT5S",
                    "libroabierto.listener.bloque-inicial=0",
                    "libroabierto.listener.confirmaciones=0",
                    "libroabierto.listener.max-bloques-por-lote=2000",
                    "libroabierto.triage.habilitado=false",
                    "libroabierto.triage.intervalo=PT10S",
                    "libroabierto.triage.modelo=claude-opus-5-5",
                    "libroabierto.triage.lote=10",
                    "libroabierto.triage.api-key=");

    @Test
    void sinClaveDelRelayerLaAplicacionNoArranca() {
        contexto.withPropertyValues("libroabierto.blockchain.relayer-private-key=")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("relayerPrivateKey"));
    }

    @Test
    void conClaveArmaElWorkerYNoExponeLaClaveAlImprimirLaConfiguracion() {
        contexto.withPropertyValues("libroabierto.blockchain.relayer-private-key=" + CLAVE)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PublicarQuejasPendientes.class);
                    assertThat(ctx).hasSingleBean(ProcesarEventosOnChain.class);
                    assertThat(ctx.getBean(LibroAbiertoProperties.class).toString()).doesNotContain(CLAVE);
                });
    }
}
