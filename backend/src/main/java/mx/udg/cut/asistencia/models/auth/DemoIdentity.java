package mx.udg.cut.asistencia.models.auth;

import mx.udg.cut.asistencia.models.teacher.Role;

import java.io.Serializable;
import java.util.UUID;

/** Identidad de sesión usada por el proveedor de autenticación de demostración. */
public record DemoIdentity(
        UUID id,
        String code,
        String name,
        Role role
) implements Serializable {
}
