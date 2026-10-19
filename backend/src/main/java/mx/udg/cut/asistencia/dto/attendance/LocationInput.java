package mx.udg.cut.asistencia.dto.attendance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record LocationInput(
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal latitude,
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal longitude,
        @NotNull
        @DecimalMin("0")
        @DecimalMax("100000")
        BigDecimal accuracyMeters,
        @NotNull
        Instant capturedAt
) {
}
