package ar.libroabierto.listener.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ListenerCheckpointJpaRepository extends JpaRepository<ListenerCheckpointEntity, String> {
}
