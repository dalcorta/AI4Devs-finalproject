package ar.libroabierto.complaint.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reglas de la consulta pública: qué búsquedas se aceptan y cómo se pagina. */
public class ConsultaPublica {

    static final int MIN_BUSQUEDA = 2;
    static final int MAX_EMPRESAS = 20;
    static final int TAMANIO_PAGINA = 20;

    private final ConsultasPublicas consultas;

    public ConsultaPublica(ConsultasPublicas consultas) {
        this.consultas = consultas;
    }

    /** Un texto con solo dígitos y guiones se busca como prefijo de CUIT; cualquier otro, en la razón social. */
    public List<ConsultasPublicas.EmpresaResumen> buscarEmpresas(String q) {
        String texto = q == null ? "" : q.strip();
        if (texto.length() < MIN_BUSQUEDA) {
            throw new DatosInvalidosException(
                    Map.of("q", "La búsqueda necesita al menos " + MIN_BUSQUEDA + " caracteres"));
        }
        if (texto.matches("[\\d-]+")) {
            String prefijo = texto.replace("-", "");
            // "--" pasaría el largo mínimo pero dejaría un prefijo vacío, que coincide con todas las empresas.
            if (prefijo.length() < MIN_BUSQUEDA) {
                throw new DatosInvalidosException(Map.of("q", "Un CUIT necesita al menos " + MIN_BUSQUEDA + " dígitos"));
            }
            return consultas.empresasPorCuit(prefijo, MAX_EMPRESAS);
        }
        return consultas.empresasPorRazonSocial(texto.toLowerCase(Locale.ROOT), MAX_EMPRESAS);
    }

    public Optional<List<ConsultasPublicas.QuejaResumen>> quejasDeEmpresa(String cuit, int pagina) {
        if (pagina < 0) {
            throw new DatosInvalidosException(Map.of("page", "No puede ser negativa"));
        }
        return consultas.quejasDeEmpresa(cuit, pagina, TAMANIO_PAGINA);
    }

    public Optional<ConsultasPublicas.QuejaDetalle> queja(UUID id) {
        return consultas.queja(id);
    }
}
