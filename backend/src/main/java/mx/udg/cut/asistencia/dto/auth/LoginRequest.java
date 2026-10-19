package mx.udg.cut.asistencia.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{7}$", message = "El código debe tener exactamente siete dígitos")
        String code,
        String password
) {
}
