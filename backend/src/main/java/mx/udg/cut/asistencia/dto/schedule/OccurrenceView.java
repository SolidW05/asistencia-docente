package mx.udg.cut.asistencia.dto.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OccurrenceView(
        UUID id,
        LocalDate date,
        Instant startAt,
        Instant endAt
) {
}
