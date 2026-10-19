package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.teacher.TeacherView;
import mx.udg.cut.asistencia.services.teacher.TeacherService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/teachers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTeacherController {
    private final TeacherService teachers;

    public AdminTeacherController(TeacherService teachers) {
        this.teachers = teachers;
    }

    @GetMapping
    public List<TeacherView> list() {
        return teachers.list();
    }
}
