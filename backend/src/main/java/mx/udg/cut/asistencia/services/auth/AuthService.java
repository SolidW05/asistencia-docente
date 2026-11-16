package mx.udg.cut.asistencia.services.auth;

import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordina el proveedor de identidad con los docentes persistidos.
 * El acceso simulado identifica al usuario por código y no recibe contraseña.
 * Bloquea una identidad existente y conserva JEFE salvo que el proveedor indique ADMIN.
 * La reconciliación se ejecuta en una sola transacción.
 */
@Service
public class AuthService {
    private final IdentityProvider identityProvider;
    private final TeacherRepository teachers;

    /**
     * Recibe el proveedor de identidad y el repositorio de docentes.
     * El primero identifica al usuario y el segundo conserva el rol persistido.
     */
    public AuthService(IdentityProvider identityProvider, TeacherRepository teachers) {
        this.identityProvider = identityProvider;
        this.teachers = teachers;
    }

    /**
     * Solicita identidad por código y bloquea por código la fila existente.
     * Si no existe, busca por UUID o prepara una identidad nueva.
     * Actualiza el nombre y preserva JEFE salvo que el proveedor indique ADMIN.
     * Un rol ADMIN anterior puede cambiar si el proveedor ya no lo concede.
     * Guarda y devuelve la identidad persistida; no recibe ni registra contraseña.
     */
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
        // El proveedor actualiza el nombre; JEFE se conserva salvo que el proveedor otorgue ADMIN.
        teacher.setName(identity.name());
        if (identity.role() == Role.ADMIN || teacher.getRole() != Role.JEFE)
            teacher.setRole(identity.role());
        Teacher saved = teachers.save(teacher);
        return new DemoIdentity(
                saved.getId(), saved.getCode(), saved.getName(), saved.getRole());
    }
}