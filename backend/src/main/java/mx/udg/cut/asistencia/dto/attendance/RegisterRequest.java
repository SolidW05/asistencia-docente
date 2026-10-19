package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.LocationIssue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterRequest(
        @NotNull
        UUID occurrenceId,
        @Valid
        LocationInput location,
        LocationIssue locationIssue,
        @Size(max = 80)
        String reason,
        @Size(max = 3000)
        String justification
) {
}
