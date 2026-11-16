package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.attendance.AttendanceHistoryView;
import mx.udg.cut.asistencia.dto.attendance.AttendanceView;
import mx.udg.cut.asistencia.dto.attendance.CheckInput;
import mx.udg.cut.asistencia.dto.attendance.CheckRequest;
import mx.udg.cut.asistencia.dto.attendance.CheckResponse;
import mx.udg.cut.asistencia.dto.attendance.RegisterInput;
import mx.udg.cut.asistencia.dto.attendance.RegisterRequest;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.attendance.AttendanceService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone comprobación, registro y consultas de asistencia del usuario autenticado.
 * Toma el docente de la sesión en lugar de confiar en un identificador enviado en el cuerpo.
 * Convierte solicitudes validadas en entradas de servicio y recibe una clave idempotente.
 * Las reglas de tiempo, ubicación y escritura de cadena se resuelven en el servicio.
 */
@RestController
@RequestMapping("/api")
public class AttendanceController {
    private final AttendanceService service;

    /**
     * Recibe el servicio que evalúa y registra la asistencia.
     * Cada ruta usa la identidad autenticada para delimitar el propietario.
     */
    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    /**
     * Toma el docente de la sesión y valida ocurrencia, ubicación o incidencia del cuerpo.
     * Devuelve resultados espacial y temporal, estado propuesto y necesidad de justificación.
     * No registra una asistencia ni agrega un bloque a la cadena.
     */
    @PostMapping("/attendance/check")
    public CheckResponse check(
            @AuthenticationPrincipal
            DemoIdentity identity,
            @Valid
            @RequestBody
            CheckRequest request) {
        return service.check(
                identity.id(),
                new CheckInput(
                        request.occurrenceId(), request.location(), request.locationIssue()));
    }

    /**
     * Recibe docente de sesión, clave Idempotency-Key y datos validados de la ocurrencia.
     * Un reintento con la misma clave y contenido devuelve la asistencia existente.
     * Reutilizar la clave con datos distintos produce conflicto; tampoco duplica una ocurrencia.
     * La asistencia y su bloque se guardan atómicamente y se devuelve AttendanceView.
     */
    @PostMapping("/attendance")
    public AttendanceView register(
            @AuthenticationPrincipal
            DemoIdentity identity,
            @RequestHeader("Idempotency-Key")
            UUID key,
            @Valid
            @RequestBody
            RegisterRequest request) {
        return service.register(
                identity.id(),
                key,
                new RegisterInput(
                        request.occurrenceId(),
                        request.location(),
                        request.locationIssue(),
                        request.reason(),
                        request.justification()));
    }

    /**
     * Toma el docente de la sesión y devuelve únicamente sus asistencias.
     * La lista se ordena por recepción descendente e incluye el estado actual.
     * El nombre de clase mostrado procede de la evidencia inicial del registro.
     */
    @GetMapping("/attendance")
    public List<AttendanceView> history(@AuthenticationPrincipal DemoIdentity identity) {
        return service.history(identity.id());
    }

    /**
     * Recibe el UUID de asistencia y comprueba que pertenezca al docente de sesión.
     * Devuelve eventos ordenados por índice con estados, fecha y datos de revisión.
     * La vista de historia no expone los hashes de los bloques.
     */
    @GetMapping("/attendance/{attendanceId}/history")
    public List<AttendanceHistoryView> history(
            @AuthenticationPrincipal DemoIdentity identity,
            @PathVariable UUID attendanceId) {
        return service.history(identity.id(), attendanceId);
    }
    /**
     * Recibe la clave de la ruta y la combina con el docente de la sesión.
     * Devuelve la vista de la asistencia ya registrada para esa combinación.
     * Si no existe, responde con el error de solicitud no encontrada.
     */
    @GetMapping("/attendance/requests/{key}")
    public AttendanceView request(
            @AuthenticationPrincipal
            DemoIdentity identity, @PathVariable UUID key) {
        return service.request(identity.id(), key);
    }
}
