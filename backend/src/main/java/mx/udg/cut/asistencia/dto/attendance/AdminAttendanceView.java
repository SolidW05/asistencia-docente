package mx.udg.cut.asistencia.dto.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;
import mx.udg.cut.asistencia.models.attendance.SpatialResult;
import mx.udg.cut.asistencia.models.attendance.TemporalResult;

public record AdminAttendanceView(
        UUID id,
        UUID teacherId,
        String teacherName,
        String teacherCode,
        UUID classId,
        String className,
        UUID occurrenceId,
        Instant receivedAt,
        AttendanceStatus status,
        SpatialResult spatialResult,
        TemporalResult temporalResult,
        String reason,
        String justification,
        JsonNode location
) {
}