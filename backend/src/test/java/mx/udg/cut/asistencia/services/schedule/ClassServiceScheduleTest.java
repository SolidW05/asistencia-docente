package mx.udg.cut.asistencia.services.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.schedule.ClassInput;
import mx.udg.cut.asistencia.dto.schedule.ScheduleSlot;
import mx.udg.cut.asistencia.exceptions.ApiException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ClassServiceScheduleTest {
    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final ScheduleSlot MONDAY_SLOT = slot(1, 8, 10);
    private static final ScheduleSlot TUESDAY_SLOT = slot(2, 12, 13);
    private static final ScheduleSlot THURSDAY_SLOT = slot(4, 9, 13);
    private static final ScheduleSlot SATURDAY_SLOT = slot(6, 10, 12);

    @Mock private TeachingClassRepository classes;
    @Mock private ClassOccurrenceRepository occurrences;
    @Mock private TeacherRepository teachers;
    @Mock private TeacherService teacherService;
    @Mock private RoleAccessService roleAccess;
    @Mock private CampusRepository campuses;
    @Mock private GeofenceRepository geofences;
    @Mock private JdbcTemplate jdbc;
    @Mock private AppPolicy policy;

    private final Authentication admin = new UsernamePasswordAuthenticationToken("admin", "n/a");
    private final UUID teacherId = UUID.randomUUID();
    private final UUID campusId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private ClassService service;

    @BeforeEach
    void setUp() {
        service = new ClassService(
                classes, occurrences, teachers, teacherService, roleAccess, campuses, geofences, jdbc, policy);
    }

    @Test
    void createsOneClassWithFourIndependentTimeSlots() {
        allowCreation();
        allowSave();

        var saved = service.create(
                input(List.of(SATURDAY_SLOT, THURSDAY_SLOT, MONDAY_SLOT, TUESDAY_SLOT)), admin);

        assertEquals(List.of(MONDAY_SLOT, TUESDAY_SLOT, THURSDAY_SLOT, SATURDAY_SLOT), saved.schedules());
        verify(classes).saveAndFlush(any(TeachingClass.class));
        assertNull(saved.occurrence());
    }

    @ParameterizedTest
    @MethodSource("invalidSchedules")
    void rejectsInvalidSchedulesBeforeSaving(List<ScheduleSlot> slots) {
        allowCreation();

        ApiException error = assertThrows(ApiException.class, () -> service.create(input(slots), admin));

        assertEquals("INVALID_SCHEDULE", error.getCode());
        verify(classes, never()).saveAndFlush(any(TeachingClass.class));
    }

    static Stream<List<ScheduleSlot>> invalidSchedules() {
        return Stream.of(
                List.of(),
                List.of(MONDAY_SLOT, slot(1, 12, 13)),
                List.of(slot(8, 8, 10)),
                List.of(slot(4, 10, 8)),
                List.of(slot(4, 8, 8)),
                List.of(new ScheduleSlot(4, null, LocalTime.of(10, 0))));
    }

    @Test
    void usesTheTimeForEachRequestedDayAndSkipsUnscheduledDates() {
        TeachingClass assignment = assignmentWith(MONDAY_SLOT, THURSDAY_SLOT);
        when(classes.findByTeacherIdOrderByNameAscIdAsc(teacherId)).thenReturn(List.of(assignment));
        when(policy.timeZone()).thenReturn(ZONE);
        LocalDate thursday = MONDAY.plusDays(3);
        ClassOccurrence mondayOccurrence = occurrence(MONDAY, "2030-01-07T14:00:00Z", "2030-01-07T16:00:00Z");
        ClassOccurrence thursdayOccurrence = occurrence(thursday, "2030-01-10T15:00:00Z", "2030-01-10T19:00:00Z");
        when(occurrences.findByClassIdAndDate(classId, MONDAY)).thenReturn(Optional.of(mondayOccurrence));
        when(occurrences.findByClassIdAndDate(classId, thursday)).thenReturn(Optional.of(thursdayOccurrence));

        var monday = service.forTeacher(teacherId, MONDAY).getFirst();
        var thursdayView = service.forTeacher(teacherId, thursday).getFirst();
        var tuesday = service.forTeacher(teacherId, MONDAY.plusDays(1)).getFirst();

        assertEquals(mondayOccurrence.getId(), monday.occurrence().id());
        assertEquals(thursdayOccurrence.getId(), thursdayView.occurrence().id());
        assertEquals(thursdayOccurrence.getEndAt(), thursdayView.occurrence().endAt());
        assertNull(tuesday.occurrence());
        verify(jdbc).update(contains("on conflict(class_id,class_date)"), any(UUID.class), eq(classId),
                eq(teacherId), eq(MONDAY), eq(Timestamp.from(mondayOccurrence.getStartAt())),
                eq(Timestamp.from(mondayOccurrence.getEndAt())));
        verify(jdbc).update(contains("on conflict(class_id,class_date)"), any(UUID.class), eq(classId),
                eq(teacherId), eq(thursday), eq(Timestamp.from(thursdayOccurrence.getStartAt())),
                eq(Timestamp.from(thursdayOccurrence.getEndAt())));
        verify(occurrences, never()).findByClassIdAndDate(classId, MONDAY.plusDays(1));
    }

    @Test
    void updatesRetainedDaysAndRemovesOnlyUnregisteredOccurrencesOnDeletedDays() {
        TeachingClass assignment = assignmentWith(MONDAY_SLOT, THURSDAY_SLOT);
        LocalDate thursday = MONDAY.plusDays(3);
        ScheduleSlot shiftedThursday = slot(4, 11, 14);
        when(roleAccess.roleFor(admin)).thenReturn(Role.ADMIN);
        when(classes.findForUpdate(classId)).thenReturn(Optional.of(assignment));
        when(policy.timeZone()).thenReturn(ZONE);
        when(jdbc.query(contains("for update of o"), any(RowMapper.class), eq(classId), any(LocalDate.class)))
                .thenReturn(List.of(MONDAY, thursday));
        allowSave();

        var saved = service.update(classId, input(List.of(shiftedThursday, SATURDAY_SLOT)), 0, admin);

        assertEquals(List.of(shiftedThursday, SATURDAY_SLOT), saved.schedules());
        verify(jdbc).update(contains("delete from class_occurrences"), eq(classId), eq(MONDAY));
        verify(jdbc).update(contains("update class_occurrences"),
                eq(Timestamp.from(Instant.parse("2030-01-10T17:00:00Z"))),
                eq(Timestamp.from(Instant.parse("2030-01-10T20:00:00Z"))), eq(classId), eq(thursday));
    }

    @Test
    void inactiveClassKeepsTheScheduleAndDoesNotCreateAnOccurrence() {
        TeachingClass assignment = new TeachingClass(classId, "Materia", teacherId, campusId, false, Set.of());
        assignment.replaceSchedules(List.of(new ClassSchedule(1, MONDAY_SLOT.startTime(), MONDAY_SLOT.endTime())));
        when(classes.findByTeacherIdOrderByNameAscIdAsc(teacherId)).thenReturn(List.of(assignment));

        var result = service.forTeacher(teacherId, MONDAY).getFirst();

        assertEquals(List.of(MONDAY_SLOT), result.schedules());
        assertNull(result.occurrence());
        verifyNoInteractions(jdbc, occurrences);
    }

    private void allowCreation() {
        when(roleAccess.roleFor(admin)).thenReturn(Role.ADMIN);
        when(roleAccess.teacherIdFor(admin)).thenReturn(UUID.randomUUID());
    }

    private void allowSave() {
        when(teachers.existsById(teacherId)).thenReturn(true);
        when(campuses.existsById(campusId)).thenReturn(true);
        when(geofences.findAllById(List.of())).thenReturn(List.of());
        when(classes.saveAndFlush(any(TeachingClass.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ClassInput input(List<ScheduleSlot> slots) {
        return new ClassInput("Materia", teacherId, null, campusId, List.of(), slots, true);
    }

    private TeachingClass assignmentWith(ScheduleSlot... slots) {
        TeachingClass assignment = new TeachingClass(classId, "Materia", teacherId, campusId, true, Set.of());
        assignment.replaceSchedules(Stream.of(slots).map(slot ->
                new ClassSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime())).toList());
        return assignment;
    }

    private ClassOccurrence occurrence(LocalDate date, String start, String end) {
        return new ClassOccurrence(UUID.randomUUID(), classId, teacherId, date, Instant.parse(start), Instant.parse(end));
    }

    private static ScheduleSlot slot(int day, int startHour, int endHour) {
        return new ScheduleSlot(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }
}
