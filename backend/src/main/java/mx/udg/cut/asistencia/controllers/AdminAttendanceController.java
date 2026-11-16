package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.attendance.AdminAttendanceView;
import mx.udg.cut.asistencia.dto.attendance.AttendanceReviewRequest;
import mx.udg.cut.asistencia.dto.attendance.AttendanceView;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.attendance.AttendanceService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone el historial administrativo y la revisión de asistencias.
 * Spring exige ADMIN o JEFE antes de invocar estas rutas.
 * El servicio delimita las clases del jefe y valida la transición solicitada.
 * El controlador transporta identidad, parámetros y DTO validado sin calcular el resultado.
 */
@RestController
@RequestMapping("/api/admin/attendance")
@PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
public class AdminAttendanceController {
    private final AttendanceService service;

    /**
     * Recibe el servicio que concentra la consulta y revisión transaccional.
     * El controlador conserva la dependencia para delegar cada ruta.
     */
    public AdminAttendanceController(AttendanceService service) {
        this.service = service;
    }

    /**
     * Toma el identificador del actor autenticado y devuelve vistas administrativas.
     * ADMIN recibe todas las asistencias; JEFE solo las de clases que creó.
     * Los resultados se ordenan por fecha de recepción descendente e incluyen estado actual.
     */
    @GetMapping
    public List<AdminAttendanceView> list(@AuthenticationPrincipal DemoIdentity identity) {
        return service.adminHistory(identity.id());
    }

    /**
     * Recibe la asistencia por UUID, el actor de sesión y un DTO de revisión validado.
     * Solo se admite confirmar con comentario y se comprueba el alcance sobre la clase.
     * Si estaba pendiente, guarda la transición y su bloque en una transacción.
     * Devuelve la vista actual; una revisión repetida no agrega otro evento.
     */
    @PostMapping("/{attendanceId}/review")
    public AttendanceView review(
            Authentication authentication,
            @AuthenticationPrincipal DemoIdentity identity,
            @PathVariable UUID attendanceId,
            @Valid @RequestBody AttendanceReviewRequest request) {
        return service.review(authentication, identity.id(), attendanceId, request);
    }
}