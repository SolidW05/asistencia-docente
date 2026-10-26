package mx.udg.cut.asistencia.services.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import mx.udg.cut.asistencia.config.AppPolicy;
import mx.udg.cut.asistencia.dto.schedule.ClassInput;
import mx.udg.cut.asistencia.exceptions.ApiException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ClassServiceRoleScopeTest {
    @Mock private TeachingClassRepository classes;
    @Mock private ClassScheduleRepository schedules;
    @Mock private ClassOccurrenceRepository occurrences;
    @Mock private TeacherRepository teachers;
    @Mock private TeacherService teacherService;
    @Mock private RoleAccessService roleAccess;
    @Mock private CampusRepository campuses;
    @Mock private GeofenceRepository geofences;
    @Mock private JdbcTemplate jdbc;
    @Mock private AppPolicy policy;

    private final Authentication jefe = new UsernamePasswordAuthenticationToken("jefe", "n/a");
    private ClassService classService;

    @BeforeEach
    void setUp() {
        classService =
                new ClassService(
                        classes,
                        schedules,
                        occurrences,
                        teachers,
                        teacherService,
                        roleAccess,
                        campuses,
                        geofences,
                        jdbc,
                        policy);
    }

    @Test
    void jefeClassListQueriesOnlyClassesCreatedByThatJefe() {
        UUID ownerId = UUID.randomUUID();
        TeachingClass ownedClass = sampleClass("Owned", ownerId);
        when(roleAccess.roleFor(jefe)).thenReturn(Role.JEFE);
        when(roleAccess.teacherIdFor(jefe)).thenReturn(ownerId);
        when(classes.findAllByCreatedByOrderByNameAsc(ownerId)).thenReturn(List.of(ownedClass));

        var visible = classService.managedBy(jefe);

        assertEquals(List.of(ownedClass.getId()), visible.stream().map(v -> v.id()).toList());
        verify(classes).findAllByCreatedByOrderByNameAsc(ownerId);
        verify(classes, never()).findAllByOrderByNameAsc();
    }

    @Test
    void jefeCannotUpdateAClassCreatedByAnotherAccount() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        TeachingClass foreignClass = new TeachingClass(
                classId, "Clase ajena", UUID.randomUUID(), UUID.randomUUID(), true, Set.of(), otherOwnerId);
        when(roleAccess.roleFor(jefe)).thenReturn(Role.JEFE);
        when(roleAccess.teacherIdFor(jefe)).thenReturn(ownerId);
        when(classes.findWithModulesById(classId)).thenReturn(Optional.of(foreignClass));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> classService.update(classId, null, 0, jefe));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("CLASS_NOT_FOUND", exception.getCode());
        verify(classes, never()).saveAndFlush(any(TeachingClass.class));
    }

    @Test
    void adminClassListIncludesRecordsAcrossOwners() {
        Authentication admin = new UsernamePasswordAuthenticationToken("admin", "n/a");
        TeachingClass first = sampleClass("Primera", UUID.randomUUID());
        TeachingClass second = sampleClass("Segunda", UUID.randomUUID());
        when(roleAccess.roleFor(admin)).thenReturn(Role.ADMIN);
        when(classes.findAllByOrderByNameAsc()).thenReturn(List.of(first, second));

        var visible = classService.managedBy(admin);

        assertEquals(List.of(first.getId(), second.getId()), visible.stream().map(v -> v.id()).toList());
        verify(classes).findAllByOrderByNameAsc();
    }

    @Test
    void createdClassStoresAuthenticatedJefeAsOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();
        when(roleAccess.roleFor(jefe)).thenReturn(Role.JEFE);
        when(roleAccess.teacherIdFor(jefe)).thenReturn(ownerId);
        when(teachers.existsById(teacherId)).thenReturn(true);
        when(campuses.existsById(campusId)).thenReturn(true);
        when(geofences.findAllById(List.of())).thenReturn(List.of());
        AtomicReference<TeachingClass> savedClass = new AtomicReference<>();
        when(classes.saveAndFlush(any(TeachingClass.class)))
                .thenAnswer(
                        invocation -> {
                            TeachingClass saved = invocation.getArgument(0);
                            savedClass.set(saved);
                            return saved;
                        });
        when(classes.findWithModulesById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(savedClass.get()));

        classService.create(
                new ClassInput(
                        "Nueva clase",
                        teacherId,
                        null,
                        campusId,
                        List.of(),
                        2,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        true),
                jefe);

        ArgumentCaptor<TeachingClass> saved = ArgumentCaptor.forClass(TeachingClass.class);
        verify(classes).saveAndFlush(saved.capture());
        assertEquals(ownerId, saved.getValue().getCreatedBy());
    }

    private TeachingClass sampleClass(String name, UUID ownerId) {
        return new TeachingClass(
                UUID.randomUUID(), name, UUID.randomUUID(), UUID.randomUUID(), true, Set.of(), ownerId);
    }
}
