package ar.libroabierto.complaint.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos que manda el ciudadano; el usuario sale del token, nunca del cuerpo del pedido.
 *
 * @param confirmarDatosPersonales el ciudadano ya vio el aviso de HU-06 y quiere publicar la descripción tal cual
 */
public record NuevaQueja(UUID usuarioId, String cuitEmpresa, String razonSocial, String categoria,
        String descripcion, BigDecimal montoReclamado, LocalDate fechaHecho, boolean confirmarDatosPersonales) {

    /** Sin confirmación: si la descripción parece tener datos personales, se le avisa antes de guardarla. */
    public NuevaQueja(UUID usuarioId, String cuitEmpresa, String razonSocial, String categoria, String descripcion,
            BigDecimal montoReclamado, LocalDate fechaHecho) {
        this(usuarioId, cuitEmpresa, razonSocial, categoria, descripcion, montoReclamado, fechaHecho, false);
    }
}
