package ar.libroabierto.triage.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TriageJpaRepository extends JpaRepository<TriageEntity, UUID> {

    boolean existsByQuejaId(UUID quejaId);
}
