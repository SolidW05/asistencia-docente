package mx.udg.cut.asistencia.services.teacher;

import java.util.List;
import java.util.UUID;
import mx.udg.cut.asistencia.dto.teacher.TeacherView;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherService {
    private final TeacherRepository teachers;

    public TeacherService(TeacherRepository teachers) {
        this.teachers = teachers;
    }

    @Transactional(readOnly = true)
    public List<TeacherView> list() {
        return teachers.findAllByOrderByCodeAsc().stream()
                .map(t -> new TeacherView(t.getId(), t.getCode(), t.getName(), t.getRole()))
                .toList();
    }

    @Transactional
    public TeacherView createOrPromoteJefe(String code) {
        validateCode(code);
        Teacher teacher =
                teachers.lockByCode(code)
                        .orElseGet(
                                () ->
                                        new Teacher(
                                                UUID.randomUUID(),
                                                code,
                                                "Docente " + code,
                                                Role.JEFE));
        if (teacher.getRole() != Role.ADMIN) teacher.setRole(Role.JEFE);
        return view(teachers.save(teacher));
    }

    @Transactional
    public UUID getOrCreateByCode(String code) {
        validateCode(code);
        return teachers.lockByCode(code)
                .orElseGet(
                        () ->
                                teachers.save(
                                        new Teacher(
                                                UUID.randomUUID(),
                                                code,
                                                "Docente " + code,
                                                Role.TEACHER)))
                .getId();
    }

    private void validateCode(String code) {
        if (code == null || !code.matches("[0-9]{7}"))
            throw ApiException.badRequest(
                    "INVALID_TEACHER_CODE", "El código docente debe tener siete dígitos.");
    }

    private TeacherView view(Teacher teacher) {
        return new TeacherView(
                teacher.getId(), teacher.getCode(), teacher.getName(), teacher.getRole());
    }
}