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

@RestController
@RequestMapping("/api/admin/attendance")
@PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
public class AdminAttendanceController {
    private final AttendanceService service;

    public AdminAttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminAttendanceView> list(@AuthenticationPrincipal DemoIdentity identity) {
        return service.adminHistory(identity.id());
    }

    @PostMapping("/{attendanceId}/review")
    public AttendanceView review(
            Authentication authentication,
            @AuthenticationPrincipal DemoIdentity identity,
            @PathVariable UUID attendanceId,
            @Valid @RequestBody AttendanceReviewRequest request) {
        return service.review(authentication, identity.id(), attendanceId, request);
    }
}