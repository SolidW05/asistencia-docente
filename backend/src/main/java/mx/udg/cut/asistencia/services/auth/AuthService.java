package mx.udg.cut.asistencia.services.auth;

import mx.udg.cut.asistencia.models.auth.DemoIdentity;
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
        teachers.findById(identity.id())
                .ifPresentOrElse(
                        existing -> {
                            existing.setName(identity.name());
                            existing.setRole(identity.role());
                        },
                        () ->
                                teachers.save(
                                        new Teacher(
                                                identity.id(),
                                                identity.code(),
                                                identity.name(),
                                                identity.role())));
        return identity;
    }
}
