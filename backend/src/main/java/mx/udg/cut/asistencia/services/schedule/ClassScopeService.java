package mx.udg.cut.asistencia.services.schedule;

import java.util.UUID;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassScopeService {
    private final TeachingClassRepository classes;
    private final RoleAccessService roleAccess;

    public ClassScopeService(
            TeachingClassRepository classes, RoleAccessService roleAccess) {
        this.classes = classes;
        this.roleAccess = roleAccess;
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Authentication authentication, UUID classId) {
        Role role = roleAccess.roleFor(authentication);
        if (role == Role.ADMIN) return classes.existsById(classId);
        if (role != Role.JEFE) return false;
        UUID ownerId = roleAccess.teacherIdFor(authentication);
        return ownerId != null && classes.existsByIdAndCreatedBy(classId, ownerId);
    }
}