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

/**
 * Resuelve identidad y rol vigentes desde la tabla de docentes.
 * Los controladores y servicios lo usan para autorización por rol y alcance.
 * Busca primero por código y después por UUID para relacionar la sesión con la fila.
 * Un principal ausente o incompatible no obtiene rol persistido.
 */
@Service("roleAccess")
public class RoleAccessService {
    private final TeacherRepository teachers;

    /**
     * Recibe el repositorio usado para resolver el rol persistido.
     * Evita basar las decisiones solo en el rol que quedó en la sesión.
     */
    public RoleAccessService(TeacherRepository teachers) {
        this.teachers = teachers;
    }

    /**
     * Consulta el rol actual y compara su nombre con el rol solicitado.
     * Devuelve falso cuando la identidad o la fila persistida no están disponibles.
     */
    @Transactional(readOnly = true)
    public boolean hasRole(Authentication authentication, String role) {
        Role current = roleFor(authentication);
        return current != null && current.name().equals(role);
    }

    /**
     * Resuelve el rol actual y comprueba si figura entre los nombres admitidos.
     * Devuelve falso cuando no hay un rol persistido para el principal.
     */
    @Transactional(readOnly = true)
    public boolean hasAnyRole(Authentication authentication, String... roles) {
        Role current = roleFor(authentication);
        return current != null && Arrays.asList(roles).contains(current.name());
    }

    /**
     * Busca la fila vinculada a la autenticación y devuelve su rol actual.
     * Devuelve null si la autenticación no permite resolver un docente.
     */
    @Transactional(readOnly = true)
    public Role roleFor(Authentication authentication) {
        return teacherFor(authentication).map(Teacher::getRole).orElse(null);
    }

    /**
     * Resuelve el UUID de la fila vinculada a la autenticación.
     * Devuelve null si no se puede resolver un docente persistido.
     */
    @Transactional(readOnly = true)
    public UUID teacherIdFor(Authentication authentication) {
        return teacherFor(authentication).map(Teacher::getId).orElse(null);
    }

    /**
     * Reconstruye la identidad con datos actuales de la fila cuando existe.
     * Si no hay coincidencia conserva la identidad recibida.
     */
    @Transactional(readOnly = true)
    public DemoIdentity currentIdentity(DemoIdentity identity) {
        return teacherFor(identity)
                .map(t -> new DemoIdentity(t.getId(), t.getCode(), t.getName(), t.getRole()))
                .orElse(identity);
    }

    /**
     * Comprueba que la autenticación contenga un principal DemoIdentity.
     * Un principal ausente o de otro tipo produce Optional vacío.
     */
    private Optional<Teacher> teacherFor(Authentication authentication) {
        if (authentication == null) return Optional.empty();
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof DemoIdentity identity)) return Optional.empty();
        return teacherFor(identity);
    }

    /**
     * Busca por código y, si no hay coincidencia, por UUID de la identidad.
     * Una identidad nula produce Optional vacío sin consultar la base.
     */
    private Optional<Teacher> teacherFor(DemoIdentity identity) {
        if (identity == null) return Optional.empty();
        return teachers.findByCode(identity.code()).or(() -> teachers.findById(identity.id()));
    }
}