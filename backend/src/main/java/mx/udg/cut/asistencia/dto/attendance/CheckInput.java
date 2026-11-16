package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import java.util.UUID;

/** Transporta al servicio los datos validados para evaluar una asistencia. */
public record CheckInput(
        UUID occurrenceId,
        LocationInput location,
        LocationIssue locationIssue
) {
}
