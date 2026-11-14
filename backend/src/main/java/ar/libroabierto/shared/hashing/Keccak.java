package ar.libroabierto.shared.hashing;

import java.nio.charset.StandardCharsets;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public final class Keccak {

    public static final String CERO = "0x" + "0".repeat(64);

    private Keccak() {
    }

    /** keccak256 de los bytes UTF-8 del texto, en hexadecimal con prefijo 0x. */
    public static String deTexto(String texto) {
        return Numeric.toHexString(Hash.sha3(texto.getBytes(StandardCharsets.UTF_8)));
    }
}
