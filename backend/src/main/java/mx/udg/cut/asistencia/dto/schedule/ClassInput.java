package mx.udg.cut.asistencia.dto.schedule;

import java.util.List;
import java.util.UUID;

public record ClassInput(
        String name,
        UUID teacherId,
        UUID campusId,
        List<UUID> allowedModuleIds,
        int dayOfWeek,
        java.time.LocalTime startTime,
        java.time.LocalTime endTime,
        boolean active
) {
}
