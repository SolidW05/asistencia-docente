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

/**
 * Administra las identidades docentes y las designaciones de jefe.
 * Usa el repositorio para buscar, bloquear y guardar filas en transacciones.
 * Valida códigos de siete dígitos antes de crear o promover.
 * La promoción a JEFE no degrada una identidad que ya es ADMIN.
 */
@Service
public class TeacherService {
    private final TeacherRepository teachers;

    /**
     * Recibe el repositorio de identidades y roles persistidos.
     * Los métodos de escritura comparten su transacción con las operaciones JPA.
     */
    public TeacherService(TeacherRepository teachers) {
        this.teachers = teachers;
    }

    /**
     * Lee los docentes ordenados por código y los transforma en vistas administrativas.
     * Incluye los roles persistidos y no modifica las entidades.
     */
    @Transactional(readOnly = true)
    public List<TeacherView> list() {
        return teachers.findAllByOrderByCodeAsc().stream()
                .map(t -> new TeacherView(t.getId(), t.getCode(), t.getName(), t.getRole()))
                .toList();
    }

    /**
     * Valida el código y bloquea una fila existente antes de actualizarla.
     * Si no existe, prepara un docente nuevo con rol JEFE.
     * No degrada un ADMIN y devuelve la vista de la fila guardada.
     */
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

    /**
     * Valida el código y busca una fila existente con bloqueo de escritura.
     * Si falta, crea una identidad TEACHER y devuelve su UUID persistido.
     */
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

    /**
     * Exige un código no nulo de exactamente siete dígitos.
     * Devuelve un error de solicitud inválida si no cumple el formato.
     */
    private void validateCode(String code) {
        if (code == null || !code.matches("[0-9]{7}"))
            throw ApiException.badRequest(
                    "INVALID_TEACHER_CODE", "El código docente debe tener siete dígitos.");
    }

    /**
     * Construye un DTO con UUID, código, nombre y rol de la entidad.
     * No cambia el docente ni consulta otras tablas.
     */
    private TeacherView view(Teacher teacher) {
        return new TeacherView(
                teacher.getId(), teacher.getCode(), teacher.getName(), teacher.getRole());
    }
}