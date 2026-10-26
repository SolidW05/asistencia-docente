package mx.udg.cut.asistencia.controllers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.config.SecurityConfig;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.auth.AuthService;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RoleAccessService.class})
class AuthControllerRolePersistenceTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;
    @MockBean private TeacherRepository teacherRepository;

    @Test
    void loginKeepsJefeRoleInSessionAndMeReadsPersistedRole() throws Exception {
        UUID id = UUID.randomUUID();
        String code = "2468101";
        DemoIdentity jefeIdentity = new DemoIdentity(id, code, "Jefa de grupo", Role.JEFE);
        when(authService.login(code)).thenReturn(jefeIdentity);
        when(teacherRepository.findByCode(code))
                .thenReturn(Optional.of(new Teacher(id, code, "Jefa de grupo", Role.JEFE)));

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"2468101\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.role").value("JEFE"))
                        .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("JEFE"))
                .andExpect(jsonPath("$.code").value(code));

        verify(authService).login(code);
        verify(teacherRepository).findByCode(code);
    }
}
