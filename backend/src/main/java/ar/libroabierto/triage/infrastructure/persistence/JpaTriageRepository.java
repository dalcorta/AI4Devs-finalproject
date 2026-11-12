package ar.libroabierto.triage.infrastructure.persistence;

import ar.libroabierto.triage.application.TriageRepository;
import ar.libroabierto.triage.domain.Triage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaTriageRepository implements TriageRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaTriageRepository.class);

    private final TriageJpaRepository triages;

    JpaTriageRepository(TriageJpaRepository triages) {
        this.triages = triages;
    }

    @Override
    @Transactional
    public void guardar(Triage triage) {
        // El UNIQUE de queja_id es la última defensa; este chequeo evita llegar a la excepción en el caso normal.
        if (triages.existsByQuejaId(triage.quejaId())) {
            log.info("La queja {} ya tiene triage; se conserva el existente", triage.quejaId());
            return;
        }
        triages.save(TriageEntity.de(triage));
    }
}
