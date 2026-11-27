package mx.udg.cut.asistencia.dto.schedule;

import java.time.LocalTime;

/** Define la franja semanal de una clase para un día. */
public record ScheduleSlot(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
