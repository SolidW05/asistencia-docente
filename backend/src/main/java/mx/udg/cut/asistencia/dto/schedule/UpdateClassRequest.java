package mx.udg.cut.asistencia.dto.schedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Recibe los cambios de una clase y la versión esperada. */
public record UpdateClassRequest(
        @NotNull
        @Size(min = 1, max = 120)
        String name,
        UUID teacherId,
        @Pattern(regexp = "[0-9]{7}", message = "El código docente debe tener siete dígitos.")
        String teacherCode,
        @NotNull
        UUID campusId,
        List<UUID> allowedModuleIds,
        List<ScheduleSlot> schedules,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        boolean active,
        long version
) {
    public ClassInput input() {
        return new ClassInput(
                name,
                teacherId,
                teacherCode,
                campusId,
                allowedModuleIds,
                schedules == null ? List.of(new ScheduleSlot(dayOfWeek, startTime, endTime)) : schedules,
                active);
    }
}
