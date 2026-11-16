package ar.libroabierto.complaint.infrastructure.web;

import ar.libroabierto.complaint.application.DatosPersonalesDetectadosException;
import ar.libroabierto.privacidad.domain.Hallazgo;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Cuerpo del 422 de HU-06. Devuelve los fragmentos detectados al mismo autor que los escribió, para que los encuentre
 * y los quite; nada de esto se guarda ni va a los logs.
 */
final class AvisoDeDatosPersonales {

    record Detalle(String campo, boolean revisionCompleta, List<Hallazgo> hallazgos) {
    }

    private AvisoDeDatosPersonales() {
    }

    static ResponseEntity<Map<String, Object>> respuesta(DatosPersonalesDetectadosException e) {
        String mensaje = e.revision().hallazgos().isEmpty()
                ? "No se pudo revisar si el texto incluye nombres de personas. Revisalo y confirmá que querés "
                        + "publicarlo: la publicación es permanente."
                : "El texto parece incluir datos personales. Quitalos o confirmá que querés publicarlo así: la "
                        + "publicación es permanente.";
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("mensaje", mensaje,
                "datosPersonales", new Detalle(e.campo(), e.revision().completa(), e.revision().hallazgos())));
    }
}
