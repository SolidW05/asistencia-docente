package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.config.ConfigView;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega a la interfaz los parámetros de evaluación configurados en AppPolicy.
 * La ruta forma parte de la API protegida por la configuración de seguridad.
 * La respuesta incluye precisión, antigüedad, ventanas y zona horaria.
 * No cambia la política ni evalúa asistencias.
 */
@RestController
@RequestMapping("/api")
public class ConfigController {
    private final AppPolicy policy;

    /**
     * Recibe la política configurada de asistencia.
     * La usa como fuente de los valores mostrados por la API.
     */
    public ConfigController(AppPolicy policy) {
        this.policy = policy;
    }

    /**
     * Construye ConfigView con precisión máxima, antigüedad y márgenes del horario.
     * Incluye el identificador de zona horaria configurada en la política.
     * La ruta protegida requiere sesión y no modifica estos parámetros.
     */
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
