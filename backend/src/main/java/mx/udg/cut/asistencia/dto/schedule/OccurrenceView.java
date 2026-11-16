package mx.udg.cut.asistencia.dto.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Describe una sesión concreta de una clase con fecha e intervalo. */
public record OccurrenceView(
        UUID id,
        LocalDate date,
        Instant startAt,
        Instant endAt
) {
}
