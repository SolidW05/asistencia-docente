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

@RestController
@RequestMapping("/api")
public class AttendanceController {
    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

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

    @GetMapping("/attendance")
    public List<AttendanceView> history(@AuthenticationPrincipal DemoIdentity identity) {
        return service.history(identity.id());
    }

    @GetMapping("/attendance/{attendanceId}/history")
    public List<AttendanceHistoryView> history(
            @AuthenticationPrincipal DemoIdentity identity,
            @PathVariable UUID attendanceId) {
        return service.history(identity.id(), attendanceId);
    }
    @GetMapping("/attendance/requests/{key}")
    public AttendanceView request(
            @AuthenticationPrincipal
            DemoIdentity identity, @PathVariable UUID key) {
        return service.request(identity.id(), key);
    }
}
