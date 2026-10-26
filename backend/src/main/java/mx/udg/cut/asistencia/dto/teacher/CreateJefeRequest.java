package mx.udg.cut.asistencia.dto.teacher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateJefeRequest(
        @NotBlank
        @Pattern(regexp = "[0-9]{7}", message = "El código docente debe tener siete dígitos.")
        String code
) {
}