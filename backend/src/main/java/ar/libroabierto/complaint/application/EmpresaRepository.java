package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.Empresa;
import java.util.Optional;

public interface EmpresaRepository {

    Optional<Empresa> porCuit(String cuit);
}
