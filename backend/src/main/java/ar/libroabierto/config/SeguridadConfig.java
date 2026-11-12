package ar.libroabierto.config;

import ar.libroabierto.identity.application.Autenticar;
import ar.libroabierto.identity.application.CuentasDeUsuario;
import ar.libroabierto.identity.infrastructure.BCryptClaveHasher;
import ar.libroabierto.identity.infrastructure.JwtEmisorDeTokens;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/** API sin sesión: cada pedido trae su JWT (HS256, firmado con JWT_SECRET). Lo que no está permitido, se niega. */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

    private static final int MIN_BYTES_SECRETO = 32;

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtAuthenticationConverter roles) throws Exception {
        return http
                // Sin cookies de sesión no hay CSRF que proteger: la credencial es el header Authorization.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/quejas").hasRole("CIUDADANO")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
                .build();
    }

    @Bean
    JwtAuthenticationConverter roles() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }

    @Bean
    SecretKey claveJwt(LibroAbiertoProperties props) {
        byte[] secreto = props.auth().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secreto.length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + MIN_BYTES_SECRETO + " bytes");
        }
        return new SecretKeySpec(secreto, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey clave) {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Autenticar autenticar(CuentasDeUsuario cuentas, PasswordEncoder encoder, JwtEncoder jwt,
            LibroAbiertoProperties props, Clock reloj) {
        return new Autenticar(cuentas, new BCryptClaveHasher(encoder),
                new JwtEmisorDeTokens(jwt, props.auth().duracionToken(), reloj));
    }
}
