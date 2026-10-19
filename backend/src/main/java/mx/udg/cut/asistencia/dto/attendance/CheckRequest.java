package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckRequest(
        @NotNull
        UUID occurrenceId,
        @Valid
        LocationInput location,
        LocationIssue locationIssue
) {
}
