package mx.udg.cut.asistencia.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.config.SecurityConfig;
import mx.udg.cut.asistencia.dto.attendance.AttendanceView;
import mx.udg.cut.asistencia.dto.chain.ChainView;
import mx.udg.cut.asistencia.dto.chain.VerifyView;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;
import mx.udg.cut.asistencia.models.attendance.SpatialResult;
import mx.udg.cut.asistencia.models.attendance.TemporalResult;
import mx.udg.cut.asistencia.models.chain.ChainDefinition;
import mx.udg.cut.asistencia.models.teacher.Role;
import mx.udg.cut.asistencia.models.teacher.Teacher;
import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.services.attendance.AttendanceService;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;
import mx.udg.cut.asistencia.services.chain.ChainService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ChainController.class, AdminAttendanceController.class})
@Import({SecurityConfig.class, RoleAccessService.class})
class ChainAndReviewControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ChainService chainService;
    @MockBean private AttendanceService attendanceService;
    @MockBean private TeacherRepository teacherRepository;

    private final UUID teacherId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID jefeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adminId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID attendanceId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID occurrenceId = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void teacherAndJefeCannotReadOrVerifyGlobalChain() throws Exception {
        when(teacherRepository.findByCode("1111111"))
                .thenReturn(Optional.of(new Teacher(teacherId, "1111111", "Docente", Role.TEACHER)));
        when(teacherRepository.findByCode("2222222"))
                .thenReturn(Optional.of(new Teacher(jefeId, "2222222", "Jefe", Role.JEFE)));

        for (var actor : List.of(
                auth(teacherId, "1111111", Role.TEACHER),
                auth(jefeId, "2222222", Role.JEFE))) {
            mockMvc.perform(get("/api/chain").with(authentication(actor)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/chain/verify").with(authentication(actor)))
                    .andExpect(status().isForbidden());
        }

        verify(chainService, never()).chain(any(UUID.class));
        verify(chainService, never()).verify(any(UUID.class));
    }

    @Test
    void adminCanReadAndVerifyTheGlobalChain() throws Exception {
        when(teacherRepository.findByCode("3333333"))
                .thenReturn(Optional.of(new Teacher(adminId, "3333333", "Admin", Role.ADMIN)));
        when(chainService.chain(adminId))
                .thenReturn(
                        new ChainView(
                                ChainDefinition.CHAIN_ID,
                                adminId,
                                0,
                                ChainDefinition.GENESIS_HASH,
                                1,
                                ChainDefinition.GENESIS_PHRASE,
                                ChainDefinition.GENESIS_HASH,
                                List.of()));
        when(chainService.verify(adminId)).thenReturn(new VerifyView(true, 1, null));

        mockMvc.perform(get("/api/chain").with(authentication(auth(adminId, "3333333", Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainId").value(ChainDefinition.CHAIN_ID));
        mockMvc.perform(
                        get("/api/chain/verify")
                                .with(authentication(auth(adminId, "3333333", Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        verify(chainService).chain(adminId);
        verify(chainService).verify(adminId);
    }

    @Test
    void adminAttendanceReviewRoutesRejectTeacherAndAllowJefeAndAdmin() throws Exception {
        when(teacherRepository.findByCode("1111111"))
                .thenReturn(Optional.of(new Teacher(teacherId, "1111111", "Docente", Role.TEACHER)));
        when(teacherRepository.findByCode("2222222"))
                .thenReturn(Optional.of(new Teacher(jefeId, "2222222", "Jefe", Role.JEFE)));
        when(teacherRepository.findByCode("3333333"))
                .thenReturn(Optional.of(new Teacher(adminId, "3333333", "Admin", Role.ADMIN)));
        when(attendanceService.adminHistory(jefeId)).thenReturn(List.of());
        when(attendanceService.adminHistory(adminId)).thenReturn(List.of());
        when(attendanceService.review(
                        any(Authentication.class),
                        eq(jefeId),
                        eq(attendanceId),
                        any()))
                .thenReturn(
                        new AttendanceView(
                                attendanceId,
                                "Clase de prueba",
                                occurrenceId,
                                Instant.parse("2026-10-26T09:59:30Z"),
                                AttendanceStatus.CONFIRMED,
                                SpatialResult.NO_LOCATION,
                                TemporalResult.IN_WINDOW,
                                "Ubicación pendiente",
                                "Revisión manual",
                                null));

        var teacherAuth = auth(teacherId, "1111111", Role.TEACHER);
        mockMvc.perform(get("/api/admin/attendance").with(authentication(teacherAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/admin/attendance/" + attendanceId + "/review")
                                .with(authentication(teacherAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"CONFIRMED\",\"comment\":\"Revisión\"}"))
                .andExpect(status().isForbidden());

        var jefeAuth = auth(jefeId, "2222222", Role.JEFE);
        mockMvc.perform(get("/api/admin/attendance").with(authentication(jefeAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(
                        post("/api/admin/attendance/" + attendanceId + "/review")
                                .with(authentication(jefeAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"CONFIRMED\",\"comment\":\"Revisión\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/admin/attendance").with(authentication(auth(adminId, "3333333", Role.ADMIN))))
                .andExpect(status().isOk());

        verify(attendanceService, never()).adminHistory(teacherId);
        verify(attendanceService, never())
                .review(any(Authentication.class), eq(teacherId), eq(attendanceId), any());
        verify(attendanceService).adminHistory(jefeId);
        verify(attendanceService).review(
                any(Authentication.class), eq(jefeId), eq(attendanceId), any());
        verify(attendanceService).adminHistory(adminId);
    }

    private UsernamePasswordAuthenticationToken auth(UUID id, String code, Role role) {
        return new UsernamePasswordAuthenticationToken(
                new DemoIdentity(
                        id,
                        code,
                        role == Role.ADMIN ? "Admin" : role == Role.JEFE ? "Jefe" : "Docente",
                        role),
                null,
                List.of());
    }
}
