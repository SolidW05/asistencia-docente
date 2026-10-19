package mx.udg.cut.asistencia.dto.schedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateClassRequest(
        @NotNull
        @Size(min = 1, max = 120)
        String name,
        @NotNull
        UUID teacherId,
        @NotNull
        UUID campusId,
        List<UUID> allowedModuleIds,
        int dayOfWeek,
        @NotNull
        LocalTime startTime,
        @NotNull
        LocalTime endTime,
        boolean active
) {
    public ClassInput input() {
        return new ClassInput(
                name, teacherId, campusId, allowedModuleIds, dayOfWeek, startTime, endTime, active);
    }
}
