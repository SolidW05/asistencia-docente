package mx.udg.cut.asistencia.services.schedule;

import java.util.UUID;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Determina el alcance de una identidad sobre una clase concreta.
 * Consulta el rol y UUID persistidos mediante RoleAccessService.
 * ADMIN accede a clases existentes; JEFE solo a clases creadas por él.
 * La consulta no modifica ni materializa ocurrencias.
 */
@Service
public class ClassScopeService {
    private final TeachingClassRepository classes;
    private final RoleAccessService roleAccess;

    /**
     * Recibe el repositorio de clases y el resolvedor de roles vigentes.
     * La combinación permite comprobar existencia y propiedad sin confiar en el cliente.
     */
    public ClassScopeService(
            TeachingClassRepository classes, RoleAccessService roleAccess) {
        this.classes = classes;
        this.roleAccess = roleAccess;
    }

    /**
     * Concede a ADMIN acceso a una clase existente.
     * Para JEFE exige identidad persistida y que sea el creador; otros roles se rechazan.
     */
    @Transactional(readOnly = true)
    public boolean canAccess(Authentication authentication, UUID classId) {
        Role role = roleAccess.roleFor(authentication);
        if (role == Role.ADMIN) return classes.existsById(classId);
        if (role != Role.JEFE) return false;
        UUID ownerId = roleAccess.teacherIdFor(authentication);
        return ownerId != null && classes.existsByIdAndCreatedBy(classId, ownerId);
    }
}