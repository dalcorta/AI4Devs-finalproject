package ar.libroabierto.shared.hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.erdtman.jcs.JsonCanonicalizer;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Hash del contenido público que se ancla on-chain: keccak256 de los bytes UTF-8 de la forma canónica RFC 8785.
 */
public final class CanonicalHasher {

    private CanonicalHasher() {
    }

    public static String canonicalize(String json) {
        try {
            return new JsonCanonicalizer(json).getEncodedString();
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON inválido para canonicalizar", e);
        }
    }

    /** Devuelve 0x + 64 hex en minúsculas, el formato de {@code bytes32 contentHash} del contrato. */
    public static String hash(String json) {
        byte[] canonical = canonicalize(json).getBytes(StandardCharsets.UTF_8);
        return Numeric.toHexString(Hash.sha3(canonical));
    }
}
