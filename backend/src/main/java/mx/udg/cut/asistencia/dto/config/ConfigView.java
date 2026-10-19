package mx.udg.cut.asistencia.dto.config;

public record ConfigView(
        double maxAccuracyMeters,
        long maxAgeSeconds,
        int earlyMinutes,
        int lateMinutes,
        String timeZone
) {
}
