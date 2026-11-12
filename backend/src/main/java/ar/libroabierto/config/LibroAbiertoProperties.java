package ar.libroabierto.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("libroabierto")
public record LibroAbiertoProperties(@Valid @NotNull Ipfs ipfs, @Valid @NotNull Blockchain blockchain,
        @Valid @NotNull Worker worker) {

    public record Ipfs(@NotBlank String apiUrl) {
    }

    public record Blockchain(@NotBlank String rpcUrl, @NotBlank String contrato, @Positive long chainId,
            @NotBlank String relayerPrivateKey) {

        @Override
        public String toString() {
            return "Blockchain[rpcUrl=" + rpcUrl + ", contrato=" + contrato + ", chainId=" + chainId
                    + ", relayerPrivateKey=***]";
        }
    }

    public record Worker(boolean habilitado, @NotNull Duration intervalo) {
    }
}
