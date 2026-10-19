package mx.udg.cut.asistencia.services.attendance;

import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.attendance.AttendanceView;
import mx.udg.cut.asistencia.dto.attendance.CheckInput;
import mx.udg.cut.asistencia.dto.attendance.CheckResponse;
import mx.udg.cut.asistencia.dto.attendance.LocationInput;
import mx.udg.cut.asistencia.dto.attendance.RegisterInput;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.models.attendance.AttendanceRecord;
import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;
import mx.udg.cut.asistencia.models.attendance.LocationIssue;
import mx.udg.cut.asistencia.models.attendance.SpatialResult;
import mx.udg.cut.asistencia.models.attendance.TemporalResult;
import mx.udg.cut.asistencia.models.campus.Campus;
import mx.udg.cut.asistencia.models.geofence.Geofence;
import mx.udg.cut.asistencia.models.geofence.GeofenceType;
import mx.udg.cut.asistencia.models.schedule.ClassOccurrence;
import mx.udg.cut.asistencia.models.schedule.ClassSchedule;
import mx.udg.cut.asistencia.models.schedule.TeachingClass;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.attendance.AttendanceRepository;
import mx.udg.cut.asistencia.repositories.campus.CampusRepository;
import mx.udg.cut.asistencia.repositories.geofence.GeofenceRepository;
import mx.udg.cut.asistencia.repositories.schedule.ClassOccurrenceRepository;
import mx.udg.cut.asistencia.repositories.schedule.ClassScheduleRepository;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.chain.ChainAppendService;
import mx.udg.cut.asistencia.services.geofence.GeofenceService;
import mx.udg.cut.asistencia.utils.CanonicalJson;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class AttendanceService {
    private final AttendanceRepository attendances;
    private final ClassOccurrenceRepository occurrences;
    private final TeachingClassRepository classes;
    private final ClassScheduleRepository schedules;
    private final CampusRepository campuses;
    private final GeofenceRepository geofences;
    private final GeofenceService spatial;
    private final TeacherRepository teachers;
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    private final AppPolicy policy;
    private final Clock clock;
    private final ChainAppendService chain;

    public AttendanceService(
            AttendanceRepository attendances,
            ClassOccurrenceRepository occurrences,
            TeachingClassRepository classes,
            ClassScheduleRepository schedules,
            CampusRepository campuses,
            GeofenceRepository geofences,
            GeofenceService spatial,
            TeacherRepository teachers,
            JdbcTemplate jdbc,
            CanonicalJson json,
            AppPolicy policy,
            Clock clock,
            ChainAppendService chain) {
        this.attendances = attendances;
        this.occurrences = occurrences;
        this.classes = classes;
        this.schedules = schedules;
        this.campuses = campuses;
        this.geofences = geofences;
        this.spatial = spatial;
        this.teachers = teachers;
        this.jdbc = jdbc;
        this.json = json;
        this.policy = policy;
        this.clock = clock;
        this.chain = chain;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CheckResponse check(UUID teacherId, CheckInput request) {
        return evaluate(teacherId, request, clock.instant()).response();
    }

    // READ_COMMITTED es necesario aquí: después del bloqueo del docente, cada consulta debe ver
    // el registro/cabeza que confirmó la transacción anterior antes de calcular el siguiente
    // índice.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceView register(UUID teacherId, UUID idempotencyKey, RegisterInput request) {
        Teacher teacher =
                teachers.lockById(teacherId)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "TEACHER_NOT_FOUND",
                                                "No se encontró la identidad de la sesión."));
        if (request.locationIssue() != null && request.location() != null)
            throw ApiException.badRequest(
                    "INVALID_LOCATION",
                    "Cuando reportes una incidencia de ubicación, envía location como null.");
        String fingerprint = fingerprint(request);
        var previous = attendances.findByTeacherIdAndIdempotencyKey(teacherId, idempotencyKey);
        if (previous.isPresent()) {
            AttendanceRecord record = previous.get();
            if (!record.getRequestFingerprint().equals(fingerprint))
                throw ApiException.conflict(
                        "IDEMPOTENCY_KEY_REUSED",
                        "La clave de idempotencia ya se usó con datos distintos.");
            return view(record);
        }
        if (attendances
                .findByTeacherIdAndOccurrenceId(teacherId, request.occurrenceId())
                .isPresent())
            throw ApiException.conflict(
                    "ATTENDANCE_ALREADY_EXISTS",
                    "Ya existe un registro para esta ocurrencia de clase.");
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CheckInput checkInput =
                new CheckInput(request.occurrenceId(), request.location(), request.locationIssue());
        Evaluation evaluation = evaluate(teacherId, checkInput, receivedAt);
        if (evaluation.response().requiresJustification()
                && (blank(request.reason()) || blank(request.justification())))
            throw ApiException.unprocessable(
                    "JUSTIFICATION_REQUIRED",
                    "Indica un motivo y explica la situación para enviar el registro a revisión.");

        UUID attendanceId = UUID.randomUUID();
        String locationJson =
                request.location() == null || request.locationIssue() != null
                        ? null
                        : json.write(locationMap(request.location()));
        String configuration = json.write(configurationSnapshot(evaluation));
        String payload =
                json.write(
                        chainPayload(
                                teacher,
                                evaluation,
                                request,
                                receivedAt,
                                locationMap(request.location())));
        UUID occurrenceId = evaluation.occurrence().getId();
        AttendanceStatus status = evaluation.response().status();
        jdbc.update(
                """
            insert into attendances(id,teacher_id,occurrence_id,received_at,status,spatial_result,temporal_result,reason,justification,
                location,location_issue,configuration_snapshot,idempotency_key,request_fingerprint,chain_payload)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
                attendanceId,
                teacherId,
                occurrenceId,
                Timestamp.from(receivedAt),
                status.name(),
                evaluation.response().spatialResult().name(),
                evaluation.response().temporalResult().name(),
                emptyToNull(request.reason()),
                emptyToNull(request.justification()),
                locationJson,
                request.locationIssue() == null ? null : request.locationIssue().name(),
                configuration,
                idempotencyKey,
                fingerprint,
                payload);
        chain.append(teacherId, attendanceId, receivedAt, payload);
        AttendanceRecord record = attendances.findById(attendanceId).orElseThrow();
        return view(record);
    }

    @Transactional(readOnly = true)
    public List<AttendanceView> history(UUID teacherId) {
        return attendances.findByTeacherIdOrderByReceivedAtDesc(teacherId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceView request(UUID teacherId, UUID key) {
        AttendanceRecord record =
                attendances
                        .findByTeacherIdAndIdempotencyKey(teacherId, key)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "REQUEST_NOT_FOUND",
                                                "No se encontró un registro para esa clave."));
        return view(record);
    }

    private Evaluation evaluate(UUID teacherId, CheckInput input, Instant now) {
        ClassOccurrence occurrence =
                occurrences
                        .findByIdAndTeacherId(input.occurrenceId(), teacherId)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "OCCURRENCE_NOT_FOUND",
                                                "No se encontró una ocurrencia propia para registrar."));
        TeachingClass assignment =
                classes.findWithModulesById(occurrence.getClassId())
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "CLASS_NOT_FOUND",
                                                "No se encontró la clase de esta ocurrencia."));
        if (!assignment.getTeacherId().equals(teacherId))
            throw ApiException.notFound(
                    "OCCURRENCE_NOT_FOUND", "No se encontró una ocurrencia propia para registrar.");
        ClassSchedule schedule = schedules.findById(assignment.getId()).orElse(null);
        Campus campus = campuses.findById(assignment.getCampusId()).orElse(null);
        Geofence campusFence =
                geofences
                        .findFirstByCampusIdAndType(assignment.getCampusId(), GeofenceType.CAMPUS)
                        .orElse(null);
        List<Geofence> configuredModules =
                assignment.getAllowedModules().stream()
                        .filter(Geofence::isActive)
                        .filter(g -> g.getType() == GeofenceType.MODULE)
                        .filter(g -> g.getCampusId().equals(assignment.getCampusId()))
                        .filter(
                                g ->
                                        campusFence != null
                                                && campusFence.getId().equals(g.getParentId()))
                        .toList();
        boolean configured =
                assignment.isActive()
                        && campus != null
                        && campus.isActive()
                        && campusFence != null
                        && campusFence.isActive()
                        && !configuredModules.isEmpty()
                        && schedule != null;
        TemporalResult temporal = temporalResult(occurrence, now);
        SpatialResult result;
        UUID matchedModule = null;
        if (!configured) {
            result = SpatialResult.UNCONFIGURED;
        } else if (input.locationIssue() != null || input.location() == null) {
            result = SpatialResult.NO_LOCATION;
        } else {
            LocationInput location = input.location();
            long ageMillis = Duration.between(location.capturedAt(), now).toMillis();
            if (ageMillis < -5_000 || ageMillis > policy.maxAgeSeconds() * 1_000L)
                result = SpatialResult.STALE;
            else if (location.accuracyMeters().doubleValue() > policy.maxAccuracyMeters())
                result = SpatialResult.INACCURATE;
            else {
                List<UUID> allowedIds = configuredModules.stream().map(Geofence::getId).toList();
                double distance =
                        spatial.nearestBoundaryMeters(
                                assignment.getCampusId(),
                                allowedIds,
                                location.longitude().doubleValue(),
                                location.latitude().doubleValue());
                if (location.accuracyMeters().signum() > 0
                        && distance <= location.accuracyMeters().doubleValue())
                    result = SpatialResult.UNCERTAIN;
                else if (!spatial.covers(
                        campusFence.getId(),
                        location.longitude().doubleValue(),
                        location.latitude().doubleValue()))
                    result = SpatialResult.OUTSIDE_CAMPUS;
                else {
                    Geofence match =
                            configuredModules.stream()
                                    .filter(
                                            g ->
                                                    spatial.covers(
                                                            g.getId(),
                                                            location.longitude().doubleValue(),
                                                            location.latitude().doubleValue()))
                                    .findFirst()
                                    .orElse(null);
                    if (match == null)
                        result = SpatialResult.OTHER_CAMPUS_SPACE;
                    else {
                        result = SpatialResult.ALLOWED_MODULE;
                        matchedModule = match.getId();
                    }
                }
            }
        }
        boolean requires =
                result != SpatialResult.ALLOWED_MODULE || temporal != TemporalResult.IN_WINDOW;
        AttendanceStatus status =
                requires ? AttendanceStatus.PENDING_REVIEW : AttendanceStatus.CONFIRMED;
        String message = message(result, input.locationIssue());
        if (temporal == TemporalResult.OUTSIDE_WINDOW)
            message += " La solicitud está fuera de la ventana de horario configurada.";
        CheckResponse response = new CheckResponse(result, temporal, status, requires, message);
        return new Evaluation(
                response,
                occurrence,
                assignment,
                schedule,
                campus,
                campusFence,
                configuredModules,
                matchedModule);
    }

    private TemporalResult temporalResult(ClassOccurrence occurrence, Instant now) {
        Instant first = occurrence.getStartAt().minus(policy.earlyMinutes(), ChronoUnit.MINUTES);
        Instant last = occurrence.getEndAt().plus(policy.lateMinutes(), ChronoUnit.MINUTES);
        return !now.isBefore(first) && !now.isAfter(last)
                ? TemporalResult.IN_WINDOW
                : TemporalResult.OUTSIDE_WINDOW;
    }

    private String message(SpatialResult r, LocationIssue issue) {
        return switch (r) {
            case ALLOWED_MODULE -> "La ubicación está dentro de un módulo permitido.";
            case OTHER_CAMPUS_SPACE ->
                    "La ubicación está dentro del campus y fuera de los módulos asignados a esta clase.";
            case OUTSIDE_CAMPUS -> "La ubicación aparece fuera del campus configurado.";
            case NO_LOCATION ->
                    issue == LocationIssue.PERMISSION_DENIED
                            ? "El permiso de ubicación fue denegado."
                            : issue == LocationIssue.TIMEOUT
                                    ? "La ubicación tardó demasiado en responder."
                                    : issue == LocationIssue.UNAVAILABLE
                                            ? "El dispositivo no pudo obtener la ubicación."
                                            : "No se recibió una ubicación. Puedes reintentar o enviar una incidencia.";
            case STALE ->
                    "La ubicación es antigua o tiene una fecha futura. Actualízala antes de registrar.";
            case INACCURATE -> "La precisión reportada no alcanza la política configurada.";
            case UNCERTAIN ->
                    "El círculo de precisión alcanza un límite de geocerca; la pertenencia no puede confirmarse.";
            case UNCONFIGURED ->
                    "La clase no tiene geocercas activas suficientes para validar la asistencia.";
        };
    }

    private Map<String, Object> configurationSnapshot(Evaluation e) {
        Map<String, Object> snapshot = new TreeMap<>();
        snapshot.put("classId", e.assignment().getId());
        snapshot.put("classVersion", e.assignment().getVersion());
        snapshot.put("campusId", e.assignment().getCampusId());
        snapshot.put("campusActive", e.campus() != null && e.campus().isActive());
        snapshot.put("campusVersion", e.campus() == null ? null : e.campus().getVersion());
        snapshot.put(
                "campusGeofence",
                e.campusFence() == null ? null : geofenceSnapshot(e.campusFence()));
        snapshot.put(
                "allowedModules",
                e.assignment().getAllowedModules().stream()
                        .sorted(java.util.Comparator.comparing(g -> g.getId().toString()))
                        .map(this::geofenceSnapshot)
                        .toList());
        snapshot.put("matchedModuleId", e.matchedModuleId());
        snapshot.put("maxAccuracyMeters", policy.maxAccuracyMeters());
        snapshot.put("maxAgeSeconds", policy.maxAgeSeconds());
        snapshot.put("earlyMinutes", policy.earlyMinutes());
        snapshot.put("lateMinutes", policy.lateMinutes());
        snapshot.put("timeZone", policy.timeZone().getId());
        return snapshot;
    }

    private Map<String, Object> geofenceSnapshot(Geofence g) {
        Map<String, Object> ref = new TreeMap<>();
        ref.put("id", g.getId());
        ref.put("version", g.getVersion());
        ref.put("active", g.isActive());
        ref.put("campusId", g.getCampusId());
        ref.put("name", g.getName());
        ref.put("type", g.getType().name());
        ref.put("parentId", g.getParentId());
        ref.put("geometry", json.read(g.getGeometryJson()));
        return ref;
    }

    private Map<String, Object> chainPayload(
            Teacher teacher,
            Evaluation e,
            RegisterInput request,
            Instant receivedAt,
            Map<String, Object> location) {
        Map<String, Object> p = new TreeMap<>();
        p.put("teacherId", teacher.getId());
        p.put("teacherCode", teacher.getCode());
        p.put("teacherName", teacher.getName());
        p.put("teacherRole", teacher.getRole().name());
        p.put("classId", e.assignment().getId());
        p.put("className", e.assignment().getName());
        p.put("classVersion", e.assignment().getVersion());
        p.put("occurrenceId", e.occurrence().getId());
        p.put("occurrenceDate", e.occurrence().getDate());
        p.put("startAt", e.occurrence().getStartAt());
        p.put("endAt", e.occurrence().getEndAt());
        p.put("campusId", e.assignment().getCampusId());
        p.put("campusName", e.campus() == null ? null : e.campus().getName());
        p.put("location", request.locationIssue() == null ? location : null);
        p.put(
                "locationIssue",
                request.locationIssue() == null ? null : request.locationIssue().name());
        p.put("spatialResult", e.response().spatialResult().name());
        p.put("temporalResult", e.response().temporalResult().name());
        p.put("status", e.response().status().name());
        p.put("reason", emptyToNull(request.reason()));
        p.put("justification", emptyToNull(request.justification()));
        p.put("receivedAt", receivedAt);
        p.put("configuration", configurationSnapshot(e));
        return p;
    }

    private String fingerprint(RegisterInput r) {
        Map<String, Object> value = new TreeMap<>();
        value.put("occurrenceId", r.occurrenceId());
        value.put("location", r.location() == null ? null : locationMap(r.location()));
        value.put("locationIssue", r.locationIssue() == null ? null : r.locationIssue().name());
        value.put("reason", emptyToNull(r.reason()));
        value.put("justification", emptyToNull(r.justification()));
        return json.sha256(json.write(value));
    }

    private Map<String, Object> locationMap(LocationInput p) {
        if (p == null)
            return null;
        Map<String, Object> location = new TreeMap<>();
        location.put("latitude", p.latitude());
        location.put("longitude", p.longitude());
        location.put("accuracyMeters", p.accuracyMeters());
        location.put("capturedAt", p.capturedAt());
        return location;
    }

    private AttendanceView view(AttendanceRecord record) {
        String historicalName =
                json.read(record.getChainPayload()).path("className").asText("Clase no disponible");
        JsonNode location = record.getLocation() == null ? null : json.read(record.getLocation());
        return new AttendanceView(
                record.getId(),
                historicalName,
                record.getOccurrenceId(),
                record.getReceivedAt(),
                record.getStatus(),
                record.getSpatialResult(),
                record.getTemporalResult(),
                record.getReason(),
                record.getJustification(),
                location);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Evaluation(
            CheckResponse response,
            ClassOccurrence occurrence,
            TeachingClass assignment,
            ClassSchedule schedule,
            Campus campus,
            Geofence campusFence,
            List<Geofence> modules,
            UUID matchedModuleId
    ) {
    }
}
