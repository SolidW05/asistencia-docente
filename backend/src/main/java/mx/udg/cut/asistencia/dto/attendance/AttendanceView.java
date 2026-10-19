package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;
import mx.udg.cut.asistencia.models.attendance.SpatialResult;
import mx.udg.cut.asistencia.models.attendance.TemporalResult;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AttendanceView(
        UUID id,
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
