package ar.libroabierto.config;

import ar.libroabierto.complaint.application.Denunciantes;
import ar.libroabierto.complaint.application.EmpresaRepository;
import ar.libroabierto.complaint.application.RegistrarQueja;
import ar.libroabierto.complaint.application.RegistroDeQuejas;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RegistroConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    RegistrarQueja registrarQueja(Denunciantes denunciantes, EmpresaRepository empresas, RegistroDeQuejas registro,
            Clock reloj) {
        return new RegistrarQueja(denunciantes, empresas, registro, reloj);
    }
}
