package mx.udg.cut.asistencia.services.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.repositories.schedule.TeachingClassRepository;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ClassScopeServiceTest {
    @Mock private TeachingClassRepository classes;
    @Mock private RoleAccessService roleAccess;

    private ClassScopeService classScope;
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("actor", "n/a");

    @BeforeEach
    void setUp() {
        classScope = new ClassScopeService(classes, roleAccess);
    }

    @Test
    void jefeCanAccessOnlyClassCreatedByTheirAccount() {
        UUID classId = UUID.randomUUID();
        UUID jefeId = UUID.randomUUID();
        when(roleAccess.roleFor(authentication)).thenReturn(Role.JEFE);
        when(roleAccess.teacherIdFor(authentication)).thenReturn(jefeId);
        when(classes.existsByIdAndCreatedBy(classId, jefeId)).thenReturn(true);

        assertTrue(classScope.canAccess(authentication, classId));
    }

    @Test
    void jefeIsDeniedClassCreatedByAnotherAccount() {
        UUID foreignClassId = UUID.randomUUID();
        UUID jefeId = UUID.randomUUID();
        when(roleAccess.roleFor(authentication)).thenReturn(Role.JEFE);
        when(roleAccess.teacherIdFor(authentication)).thenReturn(jefeId);
        when(classes.existsByIdAndCreatedBy(foreignClassId, jefeId)).thenReturn(false);

        assertFalse(classScope.canAccess(authentication, foreignClassId));
    }

    @Test
    void adminCanAccessClassesAcrossAllOwners() {
        UUID classId = UUID.randomUUID();
        when(roleAccess.roleFor(authentication)).thenReturn(Role.ADMIN);
        when(classes.existsById(classId)).thenReturn(true);

        assertTrue(classScope.canAccess(authentication, classId));
    }
}
