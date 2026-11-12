package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.Cuit;
import ar.libroabierto.complaint.domain.Empresa;
import ar.libroabierto.complaint.domain.Queja;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-01: valida la queja y la deja PENDIENTE. Publicarla en IPFS y anclarla es trabajo del worker (patrón outbox), así
 * que registrar no depende de que IPFS o la cadena estén disponibles.
 */
public class RegistrarQueja {

    static final int LIMITE_DIARIO = 5;
    /** El "día" del límite y de la fecha del hecho es el del denunciante, no el del servidor. */
    static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final int MAX_CATEGORIA = 100;
    private static final int MAX_DESCRIPCION = 5000;
    private static final int MAX_RAZON_SOCIAL = 255;
    private static final int MAX_DIGITOS_ENTEROS_MONTO = 12;

    private final Denunciantes denunciantes;
    private final EmpresaRepository empresas;
    private final RegistroDeQuejas registro;
    private final Clock reloj;

    public RegistrarQueja(Denunciantes denunciantes, EmpresaRepository empresas, RegistroDeQuejas registro,
            Clock reloj) {
        this.denunciantes = denunciantes;
        this.empresas = empresas;
        this.registro = registro;
        this.reloj = reloj;
    }

    public UUID ejecutar(NuevaQueja nueva) {
        Denunciante denunciante = denunciantes.porId(nueva.usuarioId())
                .filter(d -> d.esCiudadano() && d.identidadVerificada())
                .orElseThrow(DenuncianteNoHabilitadoException::new);

        Instant ahora = reloj.instant();
        LocalDate hoy = ahora.atZone(ZONA).toLocalDate();
        Map<String, String> errores = new LinkedHashMap<>();
        Empresa empresa = empresa(nueva, errores);
        String categoria = texto(nueva.categoria(), "categoria", MAX_CATEGORIA, errores);
        String descripcion = texto(nueva.descripcion(), "descripcion", MAX_DESCRIPCION, errores);
        validarMonto(nueva.montoReclamado(), errores);
        if (nueva.fechaHecho() == null) {
            errores.put("fechaHecho", "Es obligatoria");
        } else if (nueva.fechaHecho().isAfter(hoy)) {
            errores.put("fechaHecho", "No puede ser posterior a hoy");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        if (registro.registradasDesde(denunciante.id(), hoy.atStartOfDay(ZONA).toInstant()) >= LIMITE_DIARIO) {
            throw new LimiteDiarioAlcanzadoException(LIMITE_DIARIO);
        }

        Queja queja = Queja.nueva(UUID.randomUUID(), denunciante.id(), empresa, categoria, descripcion,
                nueva.montoReclamado(), nueva.fechaHecho(), List.of(), ahora);
        registro.registrar(queja);
        return queja.id();
    }

    private Empresa empresa(NuevaQueja nueva, Map<String, String> errores) {
        if (!Cuit.esValido(nueva.cuitEmpresa())) {
            errores.put("cuitEmpresa", "No es un CUIT válido (11 dígitos con dígito verificador correcto)");
            return null;
        }
        Optional<Empresa> existente = empresas.porCuit(nueva.cuitEmpresa());
        if (existente.isPresent()) {
            // La razón social de una empresa ya registrada no la cambia un denunciante.
            return existente.get();
        }
        String razonSocial = texto(nueva.razonSocial(), "razonSocial", MAX_RAZON_SOCIAL, errores);
        return razonSocial == null ? null : new Empresa(nueva.cuitEmpresa(), razonSocial);
    }

    private static String texto(String valor, String campo, int maximo, Map<String, String> errores) {
        if (valor == null || valor.isBlank()) {
            errores.put(campo, "Es obligatorio");
            return null;
        }
        String limpio = valor.strip();
        if (limpio.length() > maximo) {
            errores.put(campo, "Supera los " + maximo + " caracteres");
            return null;
        }
        return limpio;
    }

    private static void validarMonto(BigDecimal monto, Map<String, String> errores) {
        if (monto == null) {
            return;
        }
        if (monto.signum() < 0) {
            errores.put("montoReclamado", "No puede ser negativo");
        } else if (monto.stripTrailingZeros().scale() > 2) {
            errores.put("montoReclamado", "Admite hasta 2 decimales");
        } else if (monto.precision() - monto.scale() > MAX_DIGITOS_ENTEROS_MONTO) {
            errores.put("montoReclamado", "Es demasiado grande");
        }
    }
}
