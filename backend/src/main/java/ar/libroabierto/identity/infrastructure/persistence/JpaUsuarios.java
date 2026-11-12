package ar.libroabierto.identity.infrastructure.persistence;

import ar.libroabierto.complaint.application.Denunciante;
import ar.libroabierto.complaint.application.Denunciantes;
import ar.libroabierto.identity.application.Cuenta;
import ar.libroabierto.identity.application.CuentasDeUsuario;
import ar.libroabierto.identity.domain.Rol;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaUsuarios implements CuentasDeUsuario, Denunciantes {

    private final UsuarioJpaRepository usuarios;

    JpaUsuarios(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cuenta> porEmail(String email) {
        return usuarios.porEmail(email).map(u -> new Cuenta(u.id(), u.passwordHash(), u.rol()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Denunciante> porId(UUID id) {
        return usuarios.findById(id)
                .map(u -> new Denunciante(u.id(), u.rol() == Rol.CIUDADANO, u.identidadVerificada()));
    }
}
