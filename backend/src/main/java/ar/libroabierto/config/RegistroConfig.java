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
import ar.libroabierto.privacidad.application.RevisarDatosPersonales;
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
            RevisarDatosPersonales revisor, Clock reloj) {
        return new RegistrarQueja(denunciantes, empresas, registro, revisor, reloj);
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
    ResponderQueja responderQueja(Respuestas respuestas, RevisarDatosPersonales revisor, Clock reloj) {
        return new ResponderQueja(respuestas, revisor, reloj);
    }
}
