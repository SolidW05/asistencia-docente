package mx.udg.cut.asistencia.services.auth;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("roleAccess")
public class RoleAccessService {
    private final TeacherRepository teachers;

    public RoleAccessService(TeacherRepository teachers) {
        this.teachers = teachers;
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Authentication authentication, String role) {
        Role current = roleFor(authentication);
        return current != null && current.name().equals(role);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyRole(Authentication authentication, String... roles) {
        Role current = roleFor(authentication);
        return current != null && Arrays.asList(roles).contains(current.name());
    }

    @Transactional(readOnly = true)
    public Role roleFor(Authentication authentication) {
        return teacherFor(authentication).map(Teacher::getRole).orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID teacherIdFor(Authentication authentication) {
        return teacherFor(authentication).map(Teacher::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public DemoIdentity currentIdentity(DemoIdentity identity) {
        return teacherFor(identity)
                .map(t -> new DemoIdentity(t.getId(), t.getCode(), t.getName(), t.getRole()))
                .orElse(identity);
    }

    private Optional<Teacher> teacherFor(Authentication authentication) {
        if (authentication == null) return Optional.empty();
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof DemoIdentity identity)) return Optional.empty();
        return teacherFor(identity);
    }

    private Optional<Teacher> teacherFor(DemoIdentity identity) {
        if (identity == null) return Optional.empty();
        return teachers.findByCode(identity.code()).or(() -> teachers.findById(identity.id()));
    }
}