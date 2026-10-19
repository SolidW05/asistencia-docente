package mx.udg.cut.asistencia.dto.schedule;

import java.util.List;
import java.util.UUID;

public record ClassView(
        UUID id,
        String name,
        UUID teacherId,
        UUID campusId,
        List<UUID> allowedModuleIds,
        Integer dayOfWeek,
        java.time.LocalTime startTime,
        java.time.LocalTime endTime,
        boolean active,
        long version,
        OccurrenceView occurrence
) {
}
