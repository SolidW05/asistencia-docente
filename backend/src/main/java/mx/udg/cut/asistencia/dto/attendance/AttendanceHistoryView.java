package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

public record AttendanceHistoryView(
        UUID id,
        AttendanceStatus previousStatus,
        AttendanceStatus status,
        Instant changedAt,
        String reviewerName,
        String comment
) {
}