package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Recibe la información necesaria para comprobar ubicación y horario sin registrar. */
public record CheckRequest(
        @NotNull
        UUID occurrenceId,
        @Valid
        LocationInput location,
        LocationIssue locationIssue
) {
}
