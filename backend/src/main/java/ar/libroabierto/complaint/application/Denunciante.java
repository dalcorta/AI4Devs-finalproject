package ar.libroabierto.complaint.application;

import java.util.UUID;

public record Denunciante(UUID id, boolean esCiudadano, boolean identidadVerificada) {
}
