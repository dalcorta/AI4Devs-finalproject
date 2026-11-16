package ar.libroabierto.complaint.application;

import ar.libroabierto.privacidad.application.Revision;

/**
 * HU-06: el texto que se va a publicar parece tener datos personales (o no se pudo revisar por completo) y el autor no
 * confirmó. El mensaje solo nombra los tipos: puede terminar en un log y nunca lleva el texto detectado.
 */
public class DatosPersonalesDetectadosException extends RuntimeException {

    private final String campo;
    private final Revision revision;

    public DatosPersonalesDetectadosException(String campo, Revision revision) {
        super("Posibles datos personales en " + campo + ": "
                + revision.hallazgos().stream().map(h -> h.tipo().name()).distinct().toList()
                + (revision.completa() ? "" : " (revisión de nombres no disponible)"));
        this.campo = campo;
        this.revision = revision;
    }

    public String campo() {
        return campo;
    }

    public Revision revision() {
        return revision;
    }
}
