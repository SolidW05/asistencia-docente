package mx.udg.cut.asistencia.controllers;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.config.SecurityConfig;
import mx.udg.cut.asistencia.dto.teacher.TeacherView;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import mx.udg.cut.asistencia.services.teacher.TeacherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTeacherController.class)
@Import({SecurityConfig.class, RoleAccessService.class})
class AdminTeacherControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private TeacherService teacherService;
    @MockBean private TeacherRepository teacherRepository;

    @Test
    void adminCanCreateJefeThroughAdminEndpoint() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminCode = "0000001";
        UUID newId = UUID.randomUUID();
        String newCode = "1234567";
        when(teacherRepository.findByCode(adminCode))
                .thenReturn(Optional.of(new Teacher(adminId, adminCode, "Admin", Role.ADMIN)));
        when(teacherService.createOrPromoteJefe(newCode))
                .thenReturn(new TeacherView(newId, newCode, "Docente " + newCode, Role.JEFE));

        mockMvc.perform(
                        post("/api/admin/teachers")
                                .with(csrf())
                                .with(authentication(authWithIdentity(adminId, adminCode, "Admin", Role.ADMIN)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"1234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(newCode))
                .andExpect(jsonPath("$.role").value("JEFE"));

        verify(teacherService).createOrPromoteJefe(newCode);
    }

    @Test
    void jefeCannotCreateAnotherJefe() throws Exception {
        UUID jefeId = UUID.randomUUID();
        String jefeCode = "7654321";
        when(teacherRepository.findByCode(jefeCode))
                .thenReturn(Optional.of(new Teacher(jefeId, jefeCode, "Jefe", Role.JEFE)));

        mockMvc.perform(
                        post("/api/admin/teachers")
                                .with(csrf())
                                .with(authentication(authWithIdentity(jefeId, jefeCode, "Jefe", Role.JEFE)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"1234567\"}"))
                .andExpect(status().isForbidden());

        verify(teacherService, never()).createOrPromoteJefe("1234567");
    }

    private UsernamePasswordAuthenticationToken authWithIdentity(
            UUID id, String code, String name, Role role) {
        DemoIdentity identity = new DemoIdentity(id, code, name, role);
        return new UsernamePasswordAuthenticationToken(identity, null, List.of());
    }
}
