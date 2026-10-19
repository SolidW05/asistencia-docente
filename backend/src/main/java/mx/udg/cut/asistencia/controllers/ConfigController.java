package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.config.ConfigView;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConfigController {
    private final AppPolicy policy;

    public ConfigController(AppPolicy policy) {
        this.policy = policy;
    }

    @GetMapping("/config")
    public ConfigView get(@AuthenticationPrincipal Object principal) {
        return new ConfigView(
                policy.maxAccuracyMeters(),
                policy.maxAgeSeconds(),
                policy.earlyMinutes(),
                policy.lateMinutes(),
                policy.timeZone().getId());
    }
}
