package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import java.util.UUID;

/** Transporta al servicio los datos validados para registrar la asistencia. */
public record RegisterInput(
        UUID occurrenceId,
        LocationInput location,
        LocationIssue locationIssue,
        String reason,
        String justification
) {
}
