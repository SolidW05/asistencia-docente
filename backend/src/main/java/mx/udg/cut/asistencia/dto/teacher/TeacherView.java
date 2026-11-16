package mx.udg.cut.asistencia.dto.teacher;

import mx.udg.cut.asistencia.models.teacher.Role;

import java.util.UUID;

/** Expone los datos administrativos y el rol de un docente. */
public record TeacherView(
        UUID id,
        String code,
        String name,
        Role role
) {
}
