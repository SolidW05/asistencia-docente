package mx.udg.cut.asistencia.dto.geofence;

import mx.udg.cut.asistencia.models.geofence.GeofenceType;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GeofenceUpdateRequest(
        @NotNull
        UUID campusId,
        @jakarta.validation.constraints.NotBlank
        @Size(max = 120)
        String name,
        @NotNull
        GeofenceType type,
        UUID parentId,
        @NotNull
        JsonNode geometry,
        boolean active,
        long version
) {
}
