package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import java.util.UUID;

public record CheckInput(
        UUID occurrenceId,
        LocationInput location,
        LocationIssue locationIssue
) {
}
