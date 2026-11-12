package ar.libroabierto.complaint.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmpresaJpaRepository extends JpaRepository<EmpresaEntity, UUID> {

    Optional<EmpresaEntity> findByCuit(String cuit);
}
