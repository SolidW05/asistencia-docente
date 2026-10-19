package mx.udg.cut.asistencia.dto.geofence;

import mx.udg.cut.asistencia.models.geofence.GeofenceType;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record GeofenceView(
        UUID id,
        UUID campusId,
        String name,
        GeofenceType type,
        UUID parentId,
        JsonNode geometry,
        boolean active,
        long version
) {
}
