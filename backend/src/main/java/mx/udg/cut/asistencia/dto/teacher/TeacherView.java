package mx.udg.cut.asistencia.dto.teacher;

import mx.udg.cut.asistencia.models.teacher.Role;

import java.util.UUID;

public record TeacherView(
        UUID id,
        String code,
        String name,
        Role role
) {
}
