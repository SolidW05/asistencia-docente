package mx.udg.cut.asistencia.config;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppPolicy {
    private final double maxAccuracyMeters;
    private final long maxAgeSeconds;
    private final int earlyMinutes;
    private final int lateMinutes;
    private final ZoneId timeZone;

    public AppPolicy(
            @Value("${app.policy.max-accuracy-meters}")
            double maxAccuracyMeters,
            @Value("${app.policy.max-age-seconds}")
            long maxAgeSeconds,
            @Value("${app.policy.early-minutes}")
            int earlyMinutes,
            @Value("${app.policy.late-minutes}")
            int lateMinutes,
            @Value("${app.policy.time-zone}")
            String timeZone) {
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxAgeSeconds = maxAgeSeconds;
        this.earlyMinutes = earlyMinutes;
        this.lateMinutes = lateMinutes;
        this.timeZone = ZoneId.of(timeZone);
        if (maxAccuracyMeters <= 0 || maxAgeSeconds <= 0 || earlyMinutes < 0 || lateMinutes < 0)
            throw new IllegalStateException("La política de asistencia tiene valores inválidos.");
    }

    public double maxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public long maxAgeSeconds() {
        return maxAgeSeconds;
    }

    public int earlyMinutes() {
        return earlyMinutes;
    }

    public int lateMinutes() {
        return lateMinutes;
    }

    public ZoneId timeZone() {
        return timeZone;
    }
}
