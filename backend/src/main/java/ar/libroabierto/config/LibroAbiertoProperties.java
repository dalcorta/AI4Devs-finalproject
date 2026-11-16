package ar.libroabierto.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("libroabierto")
public record LibroAbiertoProperties(@Valid @NotNull Ipfs ipfs, @Valid @NotNull Blockchain blockchain,
        @Valid @NotNull Worker worker, @Valid @NotNull Listener listener, @Valid @NotNull Triage triage,
        @Valid @NotNull Auth auth, @Valid @DefaultValue Privacidad privacidad) {

    public record Ipfs(@NotBlank String apiUrl) {
    }

    /**
     * @param relayerPrivateKey firma {@code fileComplaint} (RELAYER_ROLE)
     * @param officerPrivateKey clave institucional del funcionario, firma {@code changeStatus} (OFFICER_ROLE). Es otra
     *     cuenta a propósito: si se filtra la del relayer, no alcanza para cambiar estados.
     */
    public record Blockchain(@NotBlank String rpcUrl, @NotBlank String contrato, @Positive long chainId,
            @NotBlank String relayerPrivateKey, @NotBlank String officerPrivateKey) {

        @Override
        public String toString() {
            return "Blockchain[rpcUrl=" + rpcUrl + ", contrato=" + contrato + ", chainId=" + chainId
                    + ", relayerPrivateKey=***, officerPrivateKey=***]";
        }
    }

    public record Worker(boolean habilitado, @NotNull Duration intervalo) {
    }

    /**
     * @param bloqueInicial bloque desde el que se lee si no hay checkpoint: el del despliegue del contrato
     * @param confirmaciones bloques que se esperan encima de un evento antes de procesarlo (0 en Anvil)
     * @param maxBloquesPorLote tope de cada consulta eth_getLogs; los RPC públicos limitan el rango
     */
    public record Listener(boolean habilitado, @NotNull Duration intervalo, @PositiveOrZero long bloqueInicial,
            @PositiveOrZero int confirmaciones, @Positive int maxBloquesPorLote) {
    }

    /**
     * @param modelo modelo de Claude que clasifica las quejas
     * @param lote cuántas quejas toma cada pasada del worker
     * @param apiKey clave de la API de Anthropic; obligatoria si el triage está habilitado
     */
    public record Triage(boolean habilitado, @NotNull Duration intervalo, @NotBlank String modelo, @Positive int lote,
            String apiKey) {

        @Override
        public String toString() {
            return "Triage[habilitado=" + habilitado + ", intervalo=" + intervalo + ", modelo=" + modelo + ", lote="
                    + lote + ", apiKey=***]";
        }
    }

    /**
     * HU-06: detección de datos personales antes de publicar. Lee texto todavía privado, así que el modelo es siempre
     * local (Ollama); {@code PrivacidadConfig} no arranca si la URL apunta fuera de la máquina o de la red privada.
     *
     * @param llmHabilitado si es {@code false}, solo se buscan patrones y el autor siempre tiene que confirmar
     * @param ollamaUrl URL de la API de Ollama
     * @param modelo modelo de Ollama que busca nombres de personas
     * @param timeout cuánto se espera al modelo antes de seguir solo con los patrones
     */
    public record Privacidad(@DefaultValue("true") boolean llmHabilitado,
            @DefaultValue("http://127.0.0.1:11434") @NotBlank String ollamaUrl,
            @DefaultValue("qwen2.5:1.5b") @NotBlank String modelo,
            @DefaultValue("PT20S") @NotNull Duration timeout) {
    }

    /**
     * @param jwtSecret clave HMAC con la que se firman los tokens; sin valor por defecto, como la del relayer
     * @param duracionToken vigencia de cada token
     */
    public record Auth(@NotBlank String jwtSecret, @DefaultValue("PT1H") @NotNull Duration duracionToken) {

        @Override
        public String toString() {
            return "Auth[jwtSecret=***, duracionToken=" + duracionToken + "]";
        }
    }
}
