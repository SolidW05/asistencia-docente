package mx.udg.cut.asistencia.services.teacher;

import java.util.List;
import mx.udg.cut.asistencia.dto.teacher.TeacherView;
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
}
