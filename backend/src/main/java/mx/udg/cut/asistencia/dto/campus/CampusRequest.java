package mx.udg.cut.asistencia.dto.campus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Recibe los datos editables de un campus. */
public record CampusRequest(
        @NotBlank
        @Size(max = 120)
        String name,
        boolean active
) {
}
