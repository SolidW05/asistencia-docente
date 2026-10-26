package mx.udg.cut.asistencia.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import mx.udg.cut.asistencia.dto.schedule.ClassView;
import mx.udg.cut.asistencia.dto.schedule.CreateClassRequest;
import mx.udg.cut.asistencia.dto.schedule.UpdateClassRequest;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.schedule.ClassService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClassController {
    private final ClassService service;

    public ClassController(ClassService service) {
        this.service = service;
    }

    @GetMapping("/classes")
    public List<ClassView> teacherClasses(
            @AuthenticationPrincipal DemoIdentity identity,
            @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return service.forTeacher(identity.id(), date);
    }

    @GetMapping("/admin/classes")
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public List<ClassView> all(Authentication authentication) {
        return service.managedBy(authentication);
    }

    @PostMapping("/admin/classes")
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public ClassView create(
            @Valid @RequestBody CreateClassRequest request, Authentication authentication) {
        return service.create(request.input(), authentication);
    }

    @PutMapping("/admin/classes/{id}")
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public ClassView update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassRequest request,
            Authentication authentication) {
        return service.update(id, request.input(), request.version(), authentication);
    }
}