package ar.libroabierto.config;

import ar.libroabierto.complaint.application.CambiarEstadoDeQueja;
import ar.libroabierto.complaint.application.CambiosDeEstado;
import ar.libroabierto.complaint.application.ConsultaPublica;
import ar.libroabierto.complaint.application.ConsultasPublicas;
import ar.libroabierto.complaint.application.Denunciantes;
import ar.libroabierto.complaint.application.EmpresaRepository;
import ar.libroabierto.complaint.application.RegistrarQueja;
import ar.libroabierto.complaint.application.RegistroDeQuejas;
import ar.libroabierto.complaint.application.ResponderQueja;
import ar.libroabierto.complaint.application.Respuestas;
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

    @Bean
    ConsultaPublica consultaPublica(ConsultasPublicas consultas) {
        return new ConsultaPublica(consultas);
    }

    @Bean
    CambiarEstadoDeQueja cambiarEstadoDeQueja(CambiosDeEstado cambios, Clock reloj) {
        return new CambiarEstadoDeQueja(cambios, reloj);
    }

    @Bean
    ResponderQueja responderQueja(Respuestas respuestas, Clock reloj) {
        return new ResponderQueja(respuestas, reloj);
    }
}
