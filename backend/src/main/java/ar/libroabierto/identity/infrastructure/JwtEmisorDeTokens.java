package ar.libroabierto.identity.infrastructure;

import ar.libroabierto.identity.application.EmisorDeTokens;
import ar.libroabierto.identity.domain.Rol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** El token lleva solo el id interno y el rol: nada de email ni nombre. */
public class JwtEmisorDeTokens implements EmisorDeTokens {

    static final String CLAIM_ROL = "rol";
    static final String EMISOR = "libroabierto";

    private final JwtEncoder encoder;
    private final Duration duracion;
    private final Clock reloj;

    public JwtEmisorDeTokens(JwtEncoder encoder, Duration duracion, Clock reloj) {
        this.encoder = encoder;
        this.duracion = duracion;
        this.reloj = reloj;
    }

    @Override
    public String emitir(UUID usuarioId, Rol rol) {
        Instant ahora = reloj.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuarioId.toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .claim(CLAIM_ROL, rol.name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
