package mx.udg.cut.asistencia.dto.chain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Expone identidad, contenido, fecha y hashes de un bloque para su inspección. */
public record BlockView(
        UUID id,
        long index,
        String eventType,
        UUID teacherId,
        UUID attendanceId,
        Instant serverAt,
        JsonNode payload,
        String previousHash,
        String hash,
        int formatVersion
) {
}
