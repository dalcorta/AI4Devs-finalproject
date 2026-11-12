package ar.libroabierto.config;

import ar.libroabierto.triage.application.LlmClient;
import ar.libroabierto.triage.application.QuejasParaTriage;
import ar.libroabierto.triage.application.TriageRepository;
import ar.libroabierto.triage.application.TriarQuejasConfirmadas;
import ar.libroabierto.triage.application.ValidadorSalidaTriage;
import ar.libroabierto.triage.infrastructure.AnthropicLlmClient;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "libroabierto.triage.habilitado", havingValue = "true", matchIfMissing = true)
public class TriageConfig {

    @Bean(destroyMethod = "close")
    AnthropicClient anthropicClient(LibroAbiertoProperties props) {
        String apiKey = props.triage().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // Igual que la clave del relayer: nunca vive en el repositorio y sin ella la aplicación no arranca.
            throw new IllegalStateException(
                    "Falta ANTHROPIC_API_KEY para el triage (o deshabilitarlo con TRIAGE_HABILITADO=false)");
        }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    @Bean
    LlmClient llmClient(AnthropicClient cliente, LibroAbiertoProperties props) {
        return new AnthropicLlmClient(cliente, props.triage().modelo());
    }

    @Bean
    TriarQuejasConfirmadas triarQuejasConfirmadas(QuejasParaTriage pendientes, TriageRepository triages,
            LlmClient llm, LibroAbiertoProperties props) {
        return new TriarQuejasConfirmadas(pendientes, triages, llm, new ValidadorSalidaTriage(), props.triage().lote());
    }
}
