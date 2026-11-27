package mx.udg.cut.asistencia.dto.schedule;

import java.util.List;
import java.util.UUID;

/** Transporta docente, horario, campus y módulos para validar una clase. */
public record ClassInput(
        String name,
        UUID teacherId,
        String teacherCode,
        UUID campusId,
        List<UUID> allowedModuleIds,
        List<ScheduleSlot> schedules,
        boolean active
) {
}
