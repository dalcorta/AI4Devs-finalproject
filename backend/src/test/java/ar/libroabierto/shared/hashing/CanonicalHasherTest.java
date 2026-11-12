package ar.libroabierto.shared.hashing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CanonicalHasherTest {

    // Mismo archivo que usa el frontend: si backend y frontend difieren, la verificación en el navegador falla.
    private static final Path VECTORS = Path.of("..", "shared", "test-vectors", "canonical-hash.json");

    static Stream<Arguments> sharedVectors() throws IOException {
        JsonNode vectors = new ObjectMapper().readTree(VECTORS.toFile()).get("vectors");
        return StreamSupport.stream(vectors.spliterator(), false)
                .map(v -> Arguments.of(
                        v.get("name").asText(),
                        v.get("input").asText(),
                        v.get("canonical").asText(),
                        v.get("keccak256").asText()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedVectors")
    void canonicalizesLikeSharedVector(String name, String input, String canonical, String keccak256) {
        assertThat(CanonicalHasher.canonicalize(input)).isEqualTo(canonical);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedVectors")
    void hashesLikeSharedVector(String name, String input, String canonical, String keccak256) {
        assertThat(CanonicalHasher.hash(input)).isEqualTo(keccak256);
    }

    @Test
    void hashHasContractBytes32Format() {
        assertThat(CanonicalHasher.hash("{\"a\":1}")).matches("^0x[0-9a-f]{64}$");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> CanonicalHasher.canonicalize("{\"a\":"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
