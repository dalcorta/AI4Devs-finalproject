package ar.libroabierto.complaint.application;

import java.util.Optional;
import java.util.UUID;

public interface Denunciantes {

    Optional<Denunciante> porId(UUID id);
}
