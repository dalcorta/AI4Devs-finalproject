package ar.libroabierto.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, UUID> {

    @Query("select u from UsuarioEntity u where lower(u.email) = :email")
    Optional<UsuarioEntity> porEmail(String email);
}
