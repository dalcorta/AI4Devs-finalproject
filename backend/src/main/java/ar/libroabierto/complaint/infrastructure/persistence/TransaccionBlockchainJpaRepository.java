package ar.libroabierto.complaint.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransaccionBlockchainJpaRepository extends JpaRepository<TransaccionBlockchainEntity, UUID> {

    Optional<TransaccionBlockchainEntity> findByTxHash(String txHash);
}
