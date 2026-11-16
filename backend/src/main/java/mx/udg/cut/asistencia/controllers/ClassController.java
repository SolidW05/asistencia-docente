package mx.udg.cut.asistencia.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import mx.udg.cut.asistencia.dto.schedule.ClassView;
import mx.udg.cut.asistencia.dto.schedule.CreateClassRequest;
import mx.udg.cut.asistencia.dto.schedule.UpdateClassRequest;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.schedule.ClassService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone clases del docente y operaciones administrativas sobre asignaciones.
 * Las rutas administrativas requieren ADMIN o JEFE y pasan la autenticación al servicio.
 * El servicio aplica alcance por creador, validaciones y control de versión.
 * Los DTO se validan antes de convertirlos en entradas de dominio.
 */
@RestController
@RequestMapping("/api")
public class ClassController {
    private final ClassService service;

    /**
     * Recibe el servicio de clases y le delega consultas y modificaciones.
     * El servicio aplica las reglas de alcance y consistencia.
     */
    public ClassController(ClassService service) {
        this.service = service;
    }

    /**
     * Recibe una fecha ISO y el docente de la sesión, sin aceptar otro propietario.
     * Devuelve clases ordenadas por nombre con horario y ocurrencia aplicable.
     * Para clase activa en el día correspondiente puede materializar una ocurrencia.
     */
    @GetMapping("/classes")
    public List<ClassView> teacherClasses(
            @AuthenticationPrincipal DemoIdentity identity,
            @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return service.forTeacher(identity.id(), date);
    }

    /**
     * Recibe la autenticación y devuelve vistas de clases ordenadas por nombre.
     * ADMIN consulta todas; JEFE solo las que creó con su identidad persistida.
     * El resultado administrativo no materializa ocurrencias por fecha.
     */
    @GetMapping("/admin/classes")
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public List<ClassView> all(Authentication authentication) {
        return service.managedBy(authentication);
    }

    /**
     * Recibe asignación, horario, campus y módulos en el DTO validado.
     * ADMIN o JEFE se identifican mediante sesión y quedan como creadores.
     * El servicio valida relaciones y guarda clase y horario; devuelve ClassView.
     */
    @PostMapping("/admin/classes")
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public ClassView create(
            @Valid @RequestBody CreateClassRequest request, Authentication authentication) {
        return service.create(request.input(), authentication);
    }

    /**
     * Recibe UUID, configuración validada y versión esperada de la clase.
     * ADMIN puede editar; JEFE solo las clases creadas por su identidad.
     * Una versión obsoleta produce conflicto y las ocurrencias con asistencia se conservan.
     */
    @PutMapping("/admin/classes/{id}")
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public ClassView update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassRequest request,
            Authentication authentication) {
        return service.update(id, request.input(), request.version(), authentication);
    }
}