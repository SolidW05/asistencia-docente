package mx.udg.cut.asistencia.dto.campus;

import mx.udg.cut.asistencia.models.campus.Campus;

import java.util.UUID;

/** Expone los datos de un campus a la interfaz. */
public record CampusView(
        UUID id,
        String name,
        boolean active
) {
    public static CampusView from(Campus c) {
        return new CampusView(c.getId(), c.getName(), c.isActive());
    }
}
