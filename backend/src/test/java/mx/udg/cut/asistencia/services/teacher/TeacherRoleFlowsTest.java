package mx.udg.cut.asistencia.services.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.auth.AuthService;
import mx.udg.cut.asistencia.services.auth.IdentityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherRoleFlowsTest {
    @Mock private TeacherRepository teachers;
    @Mock private IdentityProvider identityProvider;

    private final Map<String, Teacher> storedTeachers = new HashMap<>();
    private TeacherService teacherService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        teacherService = new TeacherService(teachers);
        authService = new AuthService(identityProvider, teachers);
        lenient().when(teachers.lockByCode(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(storedTeachers.get(invocation.getArgument(0))));
        lenient().when(teachers.findByCode(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(storedTeachers.get(invocation.getArgument(0))));
        lenient().when(teachers.findById(any(UUID.class)))
                .thenAnswer(
                        invocation ->
                                storedTeachers.values().stream()
                                        .filter(t -> t.getId().equals(invocation.getArgument(0)))
                                        .findFirst());
        lenient().when(teachers.save(any(Teacher.class)))
                .thenAnswer(
                        invocation -> {
                            Teacher teacher = invocation.getArgument(0);
                            storedTeachers.put(teacher.getCode(), teacher);
                            return teacher;
                        });
    }

    @Test
    void adminCanCreateJefeForCodeNotKnownToIdentityProvider() {
        String code = "1234567";

        var created = teacherService.createOrPromoteJefe(code);

        assertEquals(code, created.code());
        assertEquals(Role.JEFE, created.role());
        assertEquals(Role.JEFE, storedTeachers.get(code).getRole());
        assertNotNull(storedTeachers.get(code).getId());
    }

    @Test
    void loginDoesNotDowngradeJefeWhenIdentityProviderReportsTeacher() {
        String code = "2345678";
        teacherService.createOrPromoteJefe(code);
        Teacher stored = storedTeachers.get(code);
        when(identityProvider.authenticate(code))
                .thenReturn(new DemoIdentity(stored.getId(), code, "Nombre actualizado", Role.TEACHER));

        DemoIdentity loggedIn = authService.login(code);

        assertEquals(stored.getId(), loggedIn.id());
        assertEquals(Role.JEFE, loggedIn.role());
        assertEquals(Role.JEFE, storedTeachers.get(code).getRole());
        assertEquals("Nombre actualizado", storedTeachers.get(code).getName());
    }

    @Test
    void loginPersistsIdentityThatWasNotPreviouslyStored() {
        String code = "3456789";
        UUID id = UUID.randomUUID();
        when(identityProvider.authenticate(code))
                .thenReturn(new DemoIdentity(id, code, "Docente nuevo", Role.TEACHER));

        DemoIdentity loggedIn = authService.login(code);

        assertEquals(id, loggedIn.id());
        assertEquals(Role.TEACHER, loggedIn.role());
        assertEquals(id, storedTeachers.get(code).getId());
    }

    @Test
    void promotingExistingTeacherPersistsJefeRoleAndDoesNotPromoteAdmin() {
        Teacher existingTeacher = new Teacher(UUID.randomUUID(), "4567890", "Docente", Role.TEACHER);
        Teacher existingAdmin = new Teacher(UUID.randomUUID(), "5678901", "Admin", Role.ADMIN);
        storedTeachers.put(existingTeacher.getCode(), existingTeacher);
        storedTeachers.put(existingAdmin.getCode(), existingAdmin);

        var promoted = teacherService.createOrPromoteJefe(existingTeacher.getCode());
        var admin = teacherService.createOrPromoteJefe(existingAdmin.getCode());

        assertEquals(Role.JEFE, promoted.role());
        assertEquals(Role.JEFE, storedTeachers.get(existingTeacher.getCode()).getRole());
        assertEquals(Role.ADMIN, admin.role());
        assertEquals(Role.ADMIN, storedTeachers.get(existingAdmin.getCode()).getRole());
    }
}
