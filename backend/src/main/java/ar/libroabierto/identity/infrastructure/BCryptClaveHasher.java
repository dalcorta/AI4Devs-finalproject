package ar.libroabierto.identity.infrastructure;

import ar.libroabierto.identity.application.ClaveHasher;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

public class BCryptClaveHasher implements ClaveHasher {

    private final PasswordEncoder encoder;
    private final String relleno;

    public BCryptClaveHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.relleno = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public boolean coincide(String clave, String hash) {
        return encoder.matches(clave, hash);
    }

    @Override
    public String hashDeRelleno() {
        return relleno;
    }
}
