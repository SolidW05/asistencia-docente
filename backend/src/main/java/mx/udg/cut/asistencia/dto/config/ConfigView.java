package mx.udg.cut.asistencia.dto.config;

/** Expone límites de precisión, antigüedad y ventanas horarias de la política. */
public record ConfigView(
        double maxAccuracyMeters,
        long maxAgeSeconds,
        int earlyMinutes,
        int lateMinutes,
        String timeZone
) {
}
