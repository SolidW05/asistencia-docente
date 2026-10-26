package mx.udg.cut.asistencia.controllers;

import java.util.List;
import mx.udg.cut.asistencia.dto.teacher.CreateJefeRequest;
import mx.udg.cut.asistencia.dto.teacher.TeacherView;
import mx.udg.cut.asistencia.services.teacher.TeacherService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/teachers")
public class AdminTeacherController {
    private final TeacherService teachers;

    public AdminTeacherController(TeacherService teachers) {
        this.teachers = teachers;
    }

    @GetMapping
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public List<TeacherView> list() {
        return teachers.list();
    }

    @PostMapping
    @PreAuthorize("@roleAccess.hasRole(authentication, 'ADMIN')")
    public TeacherView createJefe(@Valid @RequestBody CreateJefeRequest request) {
        return teachers.createOrPromoteJefe(request.code());
    }
}