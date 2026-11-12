package ar.libroabierto.complaint.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Datos que manda el ciudadano; el usuario sale del token, nunca del cuerpo del pedido. */
public record NuevaQueja(UUID usuarioId, String cuitEmpresa, String razonSocial, String categoria,
        String descripcion, BigDecimal montoReclamado, LocalDate fechaHecho) {
}
