package ar.libroabierto.complaint.infrastructure.persistence;

import ar.libroabierto.complaint.domain.AnchorStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface QuejaJpaRepository extends JpaRepository<QuejaEntity, UUID> {

    @Query("""
            select distinct q from QuejaEntity q
            join fetch q.empresa
            left join fetch q.evidencias
            where q.anchorStatus = :estado
            order by q.createdAt""")
    List<QuejaEntity> conAnclaje(AnchorStatus estado);

    @Query("""
            select q from QuejaEntity q
            join fetch q.empresa
            left join fetch q.evidencias
            where q.contentHash = :contentHash""")
    Optional<QuejaEntity> porContentHash(String contentHash);

    @Query(value = """
            select q.id from queja q
            where q.anchor_status = 'CONFIRMADA'
              and not exists (select 1 from triage t where t.queja_id = q.id)
            order by q.created_at
            limit :limite""", nativeQuery = true)
    List<UUID> idsConfirmadasSinTriage(int limite);

    @Query("""
            select distinct q from QuejaEntity q
            join fetch q.empresa
            left join fetch q.evidencias
            where q.id in :ids""")
    List<QuejaEntity> conIds(Collection<UUID> ids);
}
