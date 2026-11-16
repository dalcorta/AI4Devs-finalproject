package ar.libroabierto.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.libroabierto.privacidad.application.ModeloLocal;
import ar.libroabierto.privacidad.application.ModeloLocalNoDisponibleException;
import ar.libroabierto.privacidad.application.RevisarDatosPersonales;
import ar.libroabierto.privacidad.application.SolicitudModeloLocal;
import ar.libroabierto.privacidad.infrastructure.OllamaModeloLocal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Regla 2 de CLAUDE.md: la detección lee texto todavía privado, así que el modelo tiene que ser local. */
class PrivacidadConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(TriageConfigTest.PropiedadesDePrueba.class, PrivacidadConfig.class)
            .withPropertyValues(
                    "libroabierto.ipfs.api-url=http://127.0.0.1:5001",
                    "libroabierto.blockchain.rpc-url=http://127.0.0.1:8545",
                    "libroabierto.blockchain.contrato=0x5FbDB2315678afecb367f032d93F642f64180aa3",
                    "libroabierto.blockchain.chain-id=31337",
                    "libroabierto.blockchain.relayer-private-key=59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
                    "libroabierto.blockchain.officer-private-key=7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6",
                    "libroabierto.worker.habilitado=true",
                    "libroabierto.worker.intervalo=PT10S",
                    "libroabierto.listener.habilitado=true",
                    "libroabierto.listener.intervalo=PT5S",
                    "libroabierto.listener.bloque-inicial=0",
                    "libroabierto.listener.confirmaciones=0",
                    "libroabierto.listener.max-bloques-por-lote=2000",
                    "libroabierto.auth.jwt-secret=secreto-de-prueba-de-al-menos-32-bytes-para-hs256",
                    "libroabierto.triage.habilitado=false",
                    "libroabierto.triage.intervalo=PT10S",
                    "libroabierto.triage.modelo=claude-opus-5-5",
                    "libroabierto.triage.lote=10");

    @Test
    void sinConfiguracionUsaOllamaEnLocalhostConUnModeloChico() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(RevisarDatosPersonales.class);
            assertThat(ctx.getBean(ModeloLocal.class)).isInstanceOf(OllamaModeloLocal.class);
            LibroAbiertoProperties.Privacidad privacidad = ctx.getBean(LibroAbiertoProperties.class).privacidad();
            assertThat(privacidad.ollamaUrl()).isEqualTo("http://127.0.0.1:11434");
            assertThat(privacidad.modelo()).isEqualTo("qwen2.5:1.5b");
            assertThat(privacidad.timeout()).isEqualTo(Duration.ofSeconds(20));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:11434", "http://127.0.0.1:11434", "http://[::1]:11434",
            "http://ollama:11434", "http://10.0.0.5:11434", "http://192.168.1.20:11434"})
    void aceptaUnModeloEnLaMaquinaOEnLaRedPrivada(String url) {
        contexto.withPropertyValues("libroabierto.privacidad.ollama-url=" + url)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://api.example.com", "http://ollama.example.com:11434", "http://8.8.8.8:11434",
            "no es una url"})
    void unModeloFueraDeLaRedLocalNoArranca(String url) {
        contexto.withPropertyValues("libroabierto.privacidad.ollama-url=" + url)
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("PII_OLLAMA_URL"));
    }

    @Test
    void conElModeloDeshabilitadoLaRevisionDeNombresNoEstaDisponible() {
        contexto.withPropertyValues("libroabierto.privacidad.llm-habilitado=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            ModeloLocal modelo = ctx.getBean(ModeloLocal.class);
            assertThatThrownBy(() -> modelo.completar(new SolicitudModeloLocal("i", "c", "{}")))
                    .isInstanceOf(ModeloLocalNoDisponibleException.class);
        });
    }
}
