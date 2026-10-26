package mx.udg.cut.asistencia.services.schedule;

import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.schedule.ClassInput;
import mx.udg.cut.asistencia.dto.schedule.ClassView;
import mx.udg.cut.asistencia.dto.schedule.OccurrenceView;
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
import mx.udg.cut.asistencia.repositories.schedule.ClassScheduleRepository;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import mx.udg.cut.asistencia.services.teacher.TeacherService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassService {
    private final TeachingClassRepository classes;
    private final ClassScheduleRepository schedules;
    private final ClassOccurrenceRepository occurrences;
    private final TeacherRepository teachers;
    private final TeacherService teacherService;
    private final RoleAccessService roleAccess;
    private final CampusRepository campuses;
    private final GeofenceRepository geofences;
    private final JdbcTemplate jdbc;
    private final AppPolicy policy;

    public ClassService(
            TeachingClassRepository classes,
            ClassScheduleRepository schedules,
            ClassOccurrenceRepository occurrences,
            TeacherRepository teachers,
            TeacherService teacherService,
            RoleAccessService roleAccess,
            CampusRepository campuses,
            GeofenceRepository geofences,
            JdbcTemplate jdbc,
            AppPolicy policy) {
        this.classes = classes;
        this.schedules = schedules;
        this.occurrences = occurrences;
        this.teachers = teachers;
        this.teacherService = teacherService;
        this.roleAccess = roleAccess;
        this.campuses = campuses;
        this.geofences = geofences;
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional
    public List<ClassView> forTeacher(UUID teacherId, LocalDate date) {
        return classes.findByTeacherIdOrderByNameAsc(teacherId).stream()
                .map(c -> view(c, date))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClassView> all() {
        return classes.findAllByOrderByNameAsc().stream().map(c -> view(c, null)).toList();
    }

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
        Set<Geofence> modules = validate(input, teacherId);
        TeachingClass saved =
                classes.saveAndFlush(
                        new TeachingClass(
                                UUID.randomUUID(),
                                input.name(),
                                teacherId,
                                input.campusId(),
                                input.active(),
                                modules,
                                creatorId));
        schedules.save(
                new ClassSchedule(
                        saved.getId(), input.dayOfWeek(), input.startTime(), input.endTime()));
        return view(classes.findWithModulesById(saved.getId()).orElseThrow(), null);
    }

    @Transactional
    public ClassView update(
            UUID id, ClassInput input, long expectedVersion, Authentication authentication) {
        Role role = roleAccess.roleFor(authentication);
        if (role != Role.ADMIN && role != Role.JEFE)
            throw ApiException.forbidden(
                    "FORBIDDEN", "Solo ADMIN y JEFE pueden actualizar clases.");

        TeachingClass current =
                classes.findWithModulesById(id)
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
        if ((!current.getTeacherId().equals(teacherId)
                        || !current.getCampusId().equals(input.campusId()))
                && jdbc.queryForObject(
                        "select exists(select 1 from class_occurrences where class_id=?)",
                        Boolean.class,
                        id))
            throw ApiException.conflict(
                    "CLASS_HAS_OCCURRENCES",
                    "La clase ya tiene ocurrencias; crea otra clase para cambiar docente o campus y conservar el historial.");
        Set<Geofence> modules = validate(input, teacherId);
        current.update(input.name(), teacherId, input.campusId(), input.active(), modules);
        TeachingClass saved = classes.saveAndFlush(current);
        ClassSchedule schedule =
                schedules
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        ApiException.unprocessable(
                                                "SCHEDULE_MISSING",
                                                "La clase no tiene horario configurado."));
        schedule.update(input.dayOfWeek(), input.startTime(), input.endTime());
        schedules.save(schedule);
        reconcileUnregisteredOccurrences(id, input, teacherId);
        return view(classes.findWithModulesById(saved.getId()).orElseThrow(), null);
    }

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

    private Set<Geofence> validate(ClassInput i, UUID teacherId) {
        if (i.name() == null || i.name().isBlank() || i.name().length() > 120)
            throw ApiException.badRequest(
                    "INVALID_NAME",
                    "El nombre de la clase es obligatorio y admite hasta 120 caracteres.");
        if (i.dayOfWeek() < 1
                || i.dayOfWeek() > 7
                || i.startTime() == null
                || i.endTime() == null
                || !i.startTime().isBefore(i.endTime()))
            throw ApiException.unprocessable(
                    "INVALID_SCHEDULE",
                    "El día debe estar entre 1 y 7 y la hora inicial debe preceder a la final.");
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

    private ClassView view(TeachingClass c, LocalDate date) {
        ClassSchedule schedule = schedules.findById(c.getId()).orElse(null);
        if (schedule == null)
            return new ClassView(
                    c.getId(),
                    c.getName(),
                    c.getTeacherId(),
                    c.getCampusId(),
                    moduleIds(c),
                    null,
                    null,
                    null,
                    c.isActive(),
                    c.getVersion(),
                    null);
        OccurrenceView occurrence = null;
        if (date != null
                && c.isActive()
                && date.getDayOfWeek().getValue() == schedule.getDayOfWeek()) {
            LocalDateTime localStart = LocalDateTime.of(date, schedule.getStartTime()),
                    localEnd = LocalDateTime.of(date, schedule.getEndTime());
            Instant
                    start =
                            localStart
                                    .atZone(policy.timeZone())
                                    .toInstant()
                                    .truncatedTo(ChronoUnit.MICROS),
                    end =
                            localEnd.atZone(policy.timeZone())
                                    .toInstant()
                                    .truncatedTo(ChronoUnit.MICROS);
            UUID generated = UUID.randomUUID();
            jdbc.update(
                    "insert into class_occurrences(id,class_id,teacher_id,class_date,start_at,end_at) values (?,?,?,?,?,?) on conflict(class_id,class_date) do nothing",
                    generated,
                    c.getId(),
                    c.getTeacherId(),
                    date,
                    Timestamp.from(start),
                    Timestamp.from(end));
            ClassOccurrence saved = occurrences.findByClassIdAndDate(c.getId(), date).orElseThrow();
            if (saved.getTeacherId().equals(c.getTeacherId()))
                occurrence =
                        new OccurrenceView(
                                saved.getId(),
                                saved.getDate(),
                                saved.getStartAt(),
                                saved.getEndAt());
        }
        return new ClassView(
                c.getId(),
                c.getName(),
                c.getTeacherId(),
                c.getCampusId(),
                moduleIds(c),
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                c.isActive(),
                c.getVersion(),
                occurrence);
    }

    private List<UUID> moduleIds(TeachingClass c) {
        return c.getAllowedModules().stream().map(Geofence::getId).sorted().toList();
    }

    private void reconcileUnregisteredOccurrences(
            UUID classId, ClassInput input, UUID teacherId) {
        LocalDate today = LocalDate.now(policy.timeZone());
        List<LocalDate> dates =
                jdbc.query(
                        "select o.class_date from class_occurrences o where o.class_id=? and o.class_date>=? and not exists(select 1 from attendances a where a.occurrence_id=o.id)",
                        (rs, row) -> rs.getObject(1, LocalDate.class),
                        classId,
                        today);
        for (LocalDate date : dates) {
            if (date.getDayOfWeek().getValue() == input.dayOfWeek()) {
                Instant start =
                        LocalDateTime.of(date, input.startTime())
                                .atZone(policy.timeZone())
                                .toInstant()
                                .truncatedTo(ChronoUnit.MICROS);
                Instant end =
                        LocalDateTime.of(date, input.endTime())
                                .atZone(policy.timeZone())
                                .toInstant()
                                .truncatedTo(ChronoUnit.MICROS);
                jdbc.update(
                        "update class_occurrences set teacher_id=?,start_at=?,end_at=? where class_id=? and class_date=?",
                        teacherId,
                        Timestamp.from(start),
                        Timestamp.from(end),
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