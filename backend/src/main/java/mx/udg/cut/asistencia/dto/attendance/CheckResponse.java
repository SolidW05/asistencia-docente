package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;
import mx.udg.cut.asistencia.models.attendance.SpatialResult;
import mx.udg.cut.asistencia.models.attendance.TemporalResult;

public record CheckResponse(
        SpatialResult spatialResult,
        TemporalResult temporalResult,
        AttendanceStatus status,
        boolean requiresJustification,
        String message
) {
}
