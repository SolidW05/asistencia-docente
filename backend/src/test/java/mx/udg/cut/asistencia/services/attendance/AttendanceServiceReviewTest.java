package mx.udg.cut.asistencia.services.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.attendance.AttendanceHistoryView;
import mx.udg.cut.asistencia.dto.attendance.AttendanceReviewRequest;
import mx.udg.cut.asistencia.dto.attendance.AttendanceView;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.models.attendance.AttendanceRecord;
import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;
import mx.udg.cut.asistencia.models.attendance.SpatialResult;
import mx.udg.cut.asistencia.models.attendance.TemporalResult;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.schedule.ClassOccurrence;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.attendance.AttendanceRepository;
import mx.udg.cut.asistencia.repositories.campus.CampusRepository;
import mx.udg.cut.asistencia.repositories.geofence.GeofenceRepository;
import mx.udg.cut.asistencia.repositories.schedule.ClassOccurrenceRepository;
import mx.udg.cut.asistencia.repositories.schedule.ClassScheduleRepository;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.chain.ChainAppendService;
import mx.udg.cut.asistencia.services.chain.ChainService;
import mx.udg.cut.asistencia.services.geofence.GeofenceService;
import mx.udg.cut.asistencia.services.schedule.ClassScopeService;
import mx.udg.cut.asistencia.utils.CanonicalJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class AttendanceServiceReviewTest {
    private final AttendanceRepository attendances = mock(AttendanceRepository.class);
    private final ClassOccurrenceRepository occurrences = mock(ClassOccurrenceRepository.class);
    private final TeachingClassRepository classes = mock(TeachingClassRepository.class);
    private final ClassScheduleRepository schedules = mock(ClassScheduleRepository.class);
    private final CampusRepository campuses = mock(CampusRepository.class);
    private final GeofenceRepository geofences = mock(GeofenceRepository.class);
    private final GeofenceService spatial = mock(GeofenceService.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AppPolicy policy = mock(AppPolicy.class);
    private final ChainAppendService chain = mock(ChainAppendService.class);
    private final ChainService chainService = mock(ChainService.class);
    private final ClassScopeService classScope = mock(ClassScopeService.class);

    private final CanonicalJson json =
            new CanonicalJson(
                    new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID reviewerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID attendanceId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID occurrenceId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID classId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final Instant fixedNow = Instant.parse("2026-10-26T10:00:00Z");

    private AttendanceService service;
    private AttendanceRecord record;
    private Authentication authentication;
    private Teacher owner;
    private Teacher reviewer;

    @BeforeEach
    void setUp() {
        service =
                new AttendanceService(
                        attendances,
                        occurrences,
                        classes,
                        schedules,
                        campuses,
                        geofences,
                        spatial,
                        teachers,
                        jdbc,
                        json,
                        policy,
                        Clock.fixed(fixedNow, ZoneOffset.UTC),
                        chain,
                        chainService,
                        classScope);
        owner = new Teacher(ownerId, "1111111", "Docente de prueba", Role.TEACHER);
        reviewer = new Teacher(reviewerId, "2222222", "Jefe de prueba", Role.JEFE);
        record = newPendingRecord();
        authentication =
                new UsernamePasswordAuthenticationToken(
                        new DemoIdentity(reviewerId, reviewer.getCode(), reviewer.getName(), Role.JEFE),
                        "",
                        List.of());

        when(attendances.findTeacherIdById(attendanceId)).thenReturn(Optional.of(ownerId));
        when(attendances.findById(attendanceId)).thenReturn(Optional.of(record));
        when(teachers.lockById(ownerId)).thenReturn(Optional.of(owner));
        when(teachers.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(occurrences.findById(occurrenceId))
                .thenReturn(
                        Optional.of(
                                new ClassOccurrence(
                                        occurrenceId,
                                        classId,
                                        ownerId,
                                        LocalDate.parse("2026-10-26"),
                                        fixedNow.minusSeconds(1800),
                                        fixedNow.plusSeconds(1800))));
        when(classScope.canAccess(authentication, classId)).thenReturn(true);
        when(attendances.saveAndFlush(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void confirmsPendingAttendanceWithExactlyOneImmutableChainEventAndRetryIsNoOp() {
        AttendanceReviewRequest request =
                new AttendanceReviewRequest(AttendanceStatus.CONFIRMED, " Validación manual ");
        AttendanceView first = service.review(authentication, reviewerId, attendanceId, request);

        assertEquals(AttendanceStatus.CONFIRMED, first.status());
        assertEquals(AttendanceStatus.CONFIRMED, record.getStatus());
        verify(attendances, times(1)).saveAndFlush(record);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chain, times(1))
                .appendStatusChanged(eq(ownerId), eq(attendanceId), eq(fixedNow), payload.capture());
        var event = json.read(payload.getValue());
        assertEquals(reviewerId.toString(), event.path("actorId").asText());
        assertEquals("JEFE", event.path("actorRole").asText());
        assertEquals("PENDING_REVIEW", event.path("previousStatus").asText());
        assertEquals("CONFIRMED", event.path("status").asText());
        assertEquals("Validación manual", event.path("reason").asText());
        assertEquals("Jefe de prueba", event.path("reviewerName").asText());

        AttendanceView retry = service.review(authentication, reviewerId, attendanceId, request);

        assertEquals(AttendanceStatus.CONFIRMED, retry.status());
        verify(attendances, times(1)).saveAndFlush(record);
        verify(chain, times(1))
                .appendStatusChanged(
                        any(UUID.class), any(UUID.class), any(Instant.class), any(String.class));
    }

    @Test
    void jefeCannotConfirmAttendanceOutsideTheirClassScope() {
        when(classScope.canAccess(authentication, classId)).thenReturn(false);

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.review(
                                        authentication,
                                        reviewerId,
                                        attendanceId,
                                        new AttendanceReviewRequest(
                                                AttendanceStatus.CONFIRMED, "Revisión")));

        assertEquals(404, error.getStatus().value());
        assertEquals("ATTENDANCE_NOT_FOUND", error.getCode());
        verify(attendances, never()).saveAndFlush(any(AttendanceRecord.class));
        verify(chain, never())
                .appendStatusChanged(
                        any(UUID.class), any(UUID.class), any(Instant.class), any(String.class));
    }

    @Test
    void teacherCannotReadAnotherTeachersAttendanceHistory() {
        UUID anotherTeacherId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(attendances.findByIdAndTeacherId(attendanceId, anotherTeacherId))
                .thenReturn(Optional.empty());

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () -> service.history(anotherTeacherId, attendanceId));

        assertEquals(404, error.getStatus().value());
        assertEquals("ATTENDANCE_NOT_FOUND", error.getCode());
        verify(chainService, never()).history(attendanceId);
    }

    @Test
    void teacherCanReadOnlyTheirOwnAttendanceHistory() {
        when(attendances.findByIdAndTeacherId(attendanceId, ownerId)).thenReturn(Optional.of(record));
        List<AttendanceHistoryView> expected =
                List.of(
                        new AttendanceHistoryView(
                                attendanceId,
                                null,
                                AttendanceStatus.PENDING_REVIEW,
                                fixedNow.minusSeconds(60),
                                null,
                                null));
        when(chainService.history(attendanceId)).thenReturn(expected);

        List<AttendanceHistoryView> actual = service.history(ownerId, attendanceId);

        assertEquals(expected, actual);
        verify(attendances).findByIdAndTeacherId(attendanceId, ownerId);
        verify(chainService).history(attendanceId);
    }

    private AttendanceRecord newPendingRecord() {
        AttendanceRecord value;
        try {
            var constructor = AttendanceRecord.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            value = constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Could not create attendance test fixture", ex);
        }
        ReflectionTestUtils.setField(value, "id", attendanceId);
        ReflectionTestUtils.setField(value, "teacherId", ownerId);
        ReflectionTestUtils.setField(value, "occurrenceId", occurrenceId);
        ReflectionTestUtils.setField(value, "receivedAt", fixedNow.minusSeconds(30));
        ReflectionTestUtils.setField(value, "status", AttendanceStatus.PENDING_REVIEW);
        ReflectionTestUtils.setField(value, "spatialResult", SpatialResult.NO_LOCATION);
        ReflectionTestUtils.setField(value, "temporalResult", TemporalResult.IN_WINDOW);
        ReflectionTestUtils.setField(value, "reason", "Permiso de ubicación denegado");
        ReflectionTestUtils.setField(value, "justification", "El sensor no respondió");
        ReflectionTestUtils.setField(value, "configurationSnapshot", "{}");
        ReflectionTestUtils.setField(value, "chainPayload", "{\"className\":\"Clase de prueba\"}");
        return value;
    }
}




