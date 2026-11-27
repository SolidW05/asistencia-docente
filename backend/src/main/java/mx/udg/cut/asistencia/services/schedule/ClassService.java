package mx.udg.cut.asistencia.services.schedule;

import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.schedule.ClassInput;
import mx.udg.cut.asistencia.dto.schedule.ClassView;
import mx.udg.cut.asistencia.dto.schedule.OccurrenceView;
import mx.udg.cut.asistencia.dto.schedule.ScheduleSlot;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.models.geofence.Geofence;
import mx.udg.cut.asistencia.models.geofence.GeofenceType;
import mx.udg.cut.asistencia.models.schedule.ClassOccurrence;
import mx.udg.cut.asistencia.models.schedule.ClassSchedule;
import mx.udg.cut.asistencia.models.schedule.TeachingClass;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.repositories.campus.CampusRepository;
import mx.udg.cut.asistencia.repositories.geofence.GeofenceRepository;
import mx.udg.cut.asistencia.repositories.schedule.ClassOccurrenceRepository;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import mx.udg.cut.asistencia.services.teacher.TeacherService;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordina asignaciones, horarios, módulos y ocurrencias de clase.
 * Las rutas administrativas usan roles actuales y restringen JEFE a su creación.
 * JPA persiste entidades; JDBC materializa ocurrencias y reconcilia fechas sin asistencia.
 * La zona horaria de política transforma los horarios locales en instantes.
 */
@Service
public class ClassService {
    private final TeachingClassRepository classes;
    private final ClassOccurrenceRepository occurrences;
    private final TeacherRepository teachers;
    private final TeacherService teacherService;
    private final RoleAccessService roleAccess;
    private final CampusRepository campuses;
    private final GeofenceRepository geofences;
    private final JdbcTemplate jdbc;
    private final AppPolicy policy;

    /**
     * Recibe repositorios de clases, ocurrencias, docentes, campus y geocercas.
     * También recibe servicios de identidad y roles, JDBC y la política de zona horaria.
     * Estas dependencias coordinan validación, persistencia y materialización de sesiones.
     */
    public ClassService(
            TeachingClassRepository classes,
            ClassOccurrenceRepository occurrences,
            TeacherRepository teachers,
            TeacherService teacherService,
            RoleAccessService roleAccess,
            CampusRepository campuses,
            GeofenceRepository geofences,
            JdbcTemplate jdbc,
            AppPolicy policy) {
        this.classes = classes;
        this.occurrences = occurrences;
        this.teachers = teachers;
        this.teacherService = teacherService;
        this.roleAccess = roleAccess;
        this.campuses = campuses;
        this.geofences = geofences;
        this.jdbc = jdbc;
        this.policy = policy;
    }

    /**
     * Lista asignaciones del docente ordenadas por nombre y construye vistas para la fecha.
     * La transacción permite crear una ocurrencia si la clase activa coincide con el día.
     */
    @Transactional
    public List<ClassView> forTeacher(UUID teacherId, LocalDate date) {
        return classes.findByTeacherIdOrderByNameAscIdAsc(teacherId).stream()
                .map(c -> view(c, date))
                .toList();
    }

    /**
     * Lista todas las clases ordenadas por nombre y las convierte sin fecha solicitada.
     * No materializa ocurrencias en esta consulta de solo lectura.
     */
    @Transactional(readOnly = true)
    public List<ClassView> all() {
        return classes.findAllByOrderByNameAsc().stream().map(c -> view(c, null)).toList();
    }

    /**
     * Resuelve el rol actual: ADMIN recibe todas las clases y JEFE solo las creadas por él.
     * Rechaza otros roles o un actor que no pueda resolverse en la base.
     */
    @Transactional(readOnly = true)
    public List<ClassView> managedBy(Authentication authentication) {
        Role role = roleAccess.roleFor(authentication);
        if (role == Role.ADMIN) return all();
        if (role != Role.JEFE)
            throw ApiException.forbidden(
                    "FORBIDDEN", "Solo ADMIN y JEFE pueden administrar clases.");
        UUID ownerId = roleAccess.teacherIdFor(authentication);
        if (ownerId == null)
            throw ApiException.forbidden("FORBIDDEN", "No se encontró el docente de la sesión.");
        return classes.findAllByCreatedByOrderByNameAsc(ownerId).stream()
                .map(c -> view(c, null))
                .toList();
    }

    /**
     * Comprueba rol y UUID del creador y resuelve el docente asignado.
     * Valida nombre, horarios, campus y módulos antes de guardar la clase.
     * Persiste la clase y sus horarios en una transacción.
     */
    @Transactional
    public ClassView create(ClassInput input, Authentication authentication) {
        Role role = roleAccess.roleFor(authentication);
        if (role != Role.ADMIN && role != Role.JEFE)
            throw ApiException.forbidden(
                    "FORBIDDEN", "Solo ADMIN y JEFE pueden crear clases.");
        UUID creatorId = roleAccess.teacherIdFor(authentication);
        if (creatorId == null)
            throw ApiException.forbidden("FORBIDDEN", "No se encontró el docente de la sesión.");

        UUID teacherId = resolveTeacherId(input);
        List<ClassSchedule> weeklySchedules = validateSchedules(input.schedules());
        Set<Geofence> modules = validate(input, teacherId);
        TeachingClass created =
                new TeachingClass(
                        UUID.randomUUID(),
                        input.name(),
                        teacherId,
                        input.campusId(),
                        input.active(),
                        modules,
                        creatorId);
        created.replaceSchedules(weeklySchedules);
        return view(classes.saveAndFlush(created), null);
    }

    /**
     * Comprueba rol, existencia y alcance del JEFE antes de comparar versión esperada.
     * Impide cambiar docente o campus cuando ya existen ocurrencias.
     * Valida la nueva configuración, guarda clase y horario y reconcilia fechas sin asistencia.
     * Devuelve la vista recargada o un error de conflicto o validación.
     */
    @Transactional
    public ClassView update(
            UUID id, ClassInput input, long expectedVersion, Authentication authentication) {
        Role role = roleAccess.roleFor(authentication);
        if (role != Role.ADMIN && role != Role.JEFE)
            throw ApiException.forbidden(
                    "FORBIDDEN", "Solo ADMIN y JEFE pueden actualizar clases.");

        TeachingClass current =
                classes.findForUpdate(id)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "CLASS_NOT_FOUND", "No se encontró la clase"));
        if (role == Role.JEFE) {
            UUID ownerId = roleAccess.teacherIdFor(authentication);
            if (ownerId == null || !ownerId.equals(current.getCreatedBy()))
                throw ApiException.notFound(
                        "CLASS_NOT_FOUND", "No se encontró la clase");
        }
        if (current.getVersion() != expectedVersion)
            throw ApiException.conflict(
                    "STALE_VERSION", "La clase cambió. Actualiza la página y vuelve a intentarlo.");

        UUID teacherId = resolveTeacherId(input);
        // Las ocurrencias existentes preservan la asociación histórica de docente y campus.
        if ((!current.getTeacherId().equals(teacherId)
                        || !current.getCampusId().equals(input.campusId()))
                && jdbc.queryForObject(
                        "select exists(select 1 from class_occurrences where class_id=?)",
                        Boolean.class,
                        id))
            throw ApiException.conflict(
                    "CLASS_HAS_OCCURRENCES",
                    "La clase ya tiene ocurrencias; crea otra clase para cambiar docente o campus y conservar el historial.");
        List<ClassSchedule> weeklySchedules = validateSchedules(input.schedules());
        Set<Geofence> modules = validate(input, teacherId);
        current.update(input.name(), teacherId, input.campusId(), input.active(), modules);
        current.replaceSchedules(weeklySchedules);
        TeachingClass saved = classes.saveAndFlush(current);
        reconcileUnregisteredOccurrences(id, weeklySchedules);
        return view(saved, null);
    }

    /**
     * Acepta código o UUID de docente, pero no ambos a la vez.
     * El código puede resolver o crear la identidad; falta de ambos produce error.
     */
    private UUID resolveTeacherId(ClassInput input) {
        if (input == null)
            throw ApiException.badRequest("INVALID_CLASS", "Los datos de la clase son obligatorios.");
        boolean hasCode = input.teacherCode() != null && !input.teacherCode().isBlank();
        if (hasCode && input.teacherId() != null)
            throw ApiException.badRequest(
                    "INVALID_TEACHER_ASSIGNMENT",
                    "Indica el docente por código o por identificador, no ambos.");
        if (hasCode) return teacherService.getOrCreateByCode(input.teacherCode());
        if (input.teacherId() != null) return input.teacherId();
        throw ApiException.badRequest(
                "TEACHER_REQUIRED", "Indica un docente existente o un código de siete dígitos.");
    }

    /**
     * Comprueba nombre y existencia de docente y campus.
     * Deduplica identificadores y exige que todos correspondan a módulos del campus.
     * Devuelve el conjunto de entidades o informa el dato inválido.
     */
    private Set<Geofence> validate(ClassInput i, UUID teacherId) {
        if (i.name() == null || i.name().isBlank() || i.name().length() > 120)
            throw ApiException.badRequest(
                    "INVALID_NAME",
                    "El nombre de la clase es obligatorio y admite hasta 120 caracteres.");
        if (!teachers.existsById(teacherId))
            throw ApiException.notFound("TEACHER_NOT_FOUND", "No se encontró el docente asignado.");
        if (!campuses.existsById(i.campusId()))
            throw ApiException.notFound("CAMPUS_NOT_FOUND", "No se encontró el campus indicado.");
        List<UUID> ids =
                i.allowedModuleIds() == null
                        ? List.of()
                        : i.allowedModuleIds().stream().distinct().toList();
        List<Geofence> found = geofences.findAllById(ids);
        if (found.size() != ids.size()
                || found.stream()
                        .anyMatch(
                                g ->
                                        g.getType() != GeofenceType.MODULE
                                                || !g.getCampusId().equals(i.campusId())))
            throw ApiException.unprocessable(
                    "INVALID_MODULE_ASSIGNMENT",
                    "Todas las geocercas asignadas deben ser módulos del campus indicado.");
        return new LinkedHashSet<>(found);
    }

    private List<ClassSchedule> validateSchedules(List<ScheduleSlot> slots) {
        if (slots == null || slots.isEmpty())
            throw ApiException.unprocessable("INVALID_SCHEDULE", "Agrega al menos un horario.");

        Set<Integer> days = new HashSet<>();
        for (ScheduleSlot slot : slots) {
            if (slot == null
                    || slot.dayOfWeek() == null
                    || slot.dayOfWeek() < 1
                    || slot.dayOfWeek() > 7
                    || slot.startTime() == null
                    || slot.endTime() == null
                    || !slot.startTime().isBefore(slot.endTime()))
                throw ApiException.unprocessable(
                        "INVALID_SCHEDULE",
                        "El día debe estar entre 1 y 7 y la hora inicial debe preceder a la final.");
            if (!days.add(slot.dayOfWeek()))
                throw ApiException.unprocessable(
                        "INVALID_SCHEDULE", "Configura un solo horario por día para la clase.");
        }
        return slots.stream()
                .sorted(Comparator.comparingInt(ScheduleSlot::dayOfWeek))
                .map(slot -> new ClassSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()))
                .toList();
    }

    /**
     * Construye una vista de la clase y su horario, admitiendo ausencia de horario.
     * Con fecha coincidente y clase activa calcula instantes en la zona configurada.
     * Inserta la ocurrencia si falta con ON CONFLICT y recupera la fila efectiva.
     * Solo expone esa ocurrencia si conserva el mismo docente asignado.
     */
    private ClassView view(TeachingClass c, LocalDate date) {
        List<ClassSchedule> weeklySchedules = c.getSchedules();
        ClassSchedule schedule = date == null ? null : scheduleFor(weeklySchedules, date);
        OccurrenceView occurrence = null;
        if (schedule != null && c.isActive()) {
            // ON CONFLICT permite que solicitudes concurrentes compartan la ocurrencia existente.
            UUID generated = UUID.randomUUID();
            jdbc.update(
                    "insert into class_occurrences(id,class_id,teacher_id,class_date,start_at,end_at) values (?,?,?,?,?,?) on conflict(class_id,class_date) do nothing",
                    generated,
                    c.getId(),
                    c.getTeacherId(),
                    date,
                    occurrenceTimestamp(date, schedule.getStartTime()),
                    occurrenceTimestamp(date, schedule.getEndTime()));
            ClassOccurrence saved = occurrences.findByClassIdAndDate(c.getId(), date).orElseThrow();
            if (saved.getTeacherId().equals(c.getTeacherId()))
                occurrence =
                        new OccurrenceView(
                                saved.getId(),
                                saved.getDate(),
                                saved.getStartAt(),
                                saved.getEndAt());
        }
        ClassSchedule legacySchedule = schedule != null || weeklySchedules.isEmpty()
                ? schedule : weeklySchedules.getFirst();
        return new ClassView(
                c.getId(),
                c.getName(),
                c.getTeacherId(),
                c.getCampusId(),
                moduleIds(c),
                weeklySchedules.stream()
                        .map(slot -> new ScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                        .toList(),
                legacySchedule == null ? null : legacySchedule.getDayOfWeek(),
                legacySchedule == null ? null : legacySchedule.getStartTime(),
                legacySchedule == null ? null : legacySchedule.getEndTime(),
                c.isActive(),
                c.getVersion(),
                occurrence);
    }

    /**
     * Extrae los UUID de los módulos asignados y los ordena.
     * La lista es una representación estable para la vista de clase.
     */
    private List<UUID> moduleIds(TeachingClass c) {
        return c.getAllowedModules().stream().map(Geofence::getId).sorted().toList();
    }

    private ClassSchedule scheduleFor(List<ClassSchedule> weeklySchedules, LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        return weeklySchedules.stream()
                .filter(schedule -> schedule.getDayOfWeek() == day)
                .findFirst()
                .orElse(null);
    }

    private Timestamp occurrenceTimestamp(LocalDate date, LocalTime time) {
        return Timestamp.from(
                LocalDateTime.of(date, time).atZone(policy.timeZone())
                        .toInstant().truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Busca desde hoy las ocurrencias de la clase que no tienen asistencia.
     * Actualiza el intervalo si el día sigue programado y elimina las demás.
     * Las ocurrencias con asistencia quedan fuera de la consulta y de la eliminación.
     */
    private void reconcileUnregisteredOccurrences(UUID classId, List<ClassSchedule> weeklySchedules) {
        LocalDate today = LocalDate.now(policy.timeZone());
        List<LocalDate> dates =
                jdbc.query(
                        """
                        select o.class_date from class_occurrences o
                        where o.class_id=? and o.class_date>=?
                          and not exists(select 1 from attendances a where a.occurrence_id=o.id)
                        for update of o
                        """,
                        (rs, row) -> rs.getObject(1, LocalDate.class),
                        classId,
                        today);
        // Una asistencia pudo confirmarse mientras esperaba el bloqueo de la ocurrencia.
        for (LocalDate date : dates) {
            ClassSchedule schedule = scheduleFor(weeklySchedules, date);
            if (schedule != null) {
                jdbc.update(
                        """
                        update class_occurrences o set start_at=?,end_at=?
                        where o.class_id=? and o.class_date=?
                          and not exists(select 1 from attendances a where a.occurrence_id=o.id)
                        """,
                        occurrenceTimestamp(date, schedule.getStartTime()),
                        occurrenceTimestamp(date, schedule.getEndTime()),
                        classId,
                        date);
            } else {
                jdbc.update(
                        "delete from class_occurrences o where o.class_id=? and o.class_date=? and not exists(select 1 from attendances a where a.occurrence_id=o.id)",
                        classId,
                        date);
            }
        }
    }
}
