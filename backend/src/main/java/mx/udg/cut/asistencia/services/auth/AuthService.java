package mx.udg.cut.asistencia.services.auth;

import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final IdentityProvider identityProvider;
    private final TeacherRepository teachers;

    public AuthService(IdentityProvider identityProvider, TeacherRepository teachers) {
        this.identityProvider = identityProvider;
        this.teachers = teachers;
    }

    /** La identidad se guarda solo cuando el usuario inicia sesión explícitamente. */
    @Transactional
    public DemoIdentity login(String code) {
        DemoIdentity identity = identityProvider.authenticate(code);
        Teacher teacher =
                teachers.lockByCode(identity.code())
                        .orElseGet(
                                () ->
                                        teachers.findById(identity.id())
                                                .orElseGet(
                                                        () ->
                                                                new Teacher(
                                                                        identity.id(),
                                                                        identity.code(),
                                                                        identity.name(),
                                                                        identity.role())));
        teacher.setName(identity.name());
        if (identity.role() == Role.ADMIN || teacher.getRole() != Role.JEFE)
            teacher.setRole(identity.role());
        Teacher saved = teachers.save(teacher);
        return new DemoIdentity(
                saved.getId(), saved.getCode(), saved.getName(), saved.getRole());
    }
}