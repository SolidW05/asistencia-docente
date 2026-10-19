package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import java.util.UUID;

public record RegisterInput(
        UUID occurrenceId,
        LocationInput location,
        LocationIssue locationIssue,
        String reason,
        String justification
) {
}
