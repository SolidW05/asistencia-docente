package mx.udg.cut.asistencia.controllers;

import java.util.List;
import mx.udg.cut.asistencia.dto.teacher.CreateJefeRequest;
import mx.udg.cut.asistencia.dto.teacher.TeacherView;
import mx.udg.cut.asistencia.services.teacher.TeacherService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone operaciones administrativas sobre identidades de docentes.
 * ADMIN y JEFE pueden consultar; solo ADMIN puede designar un jefe.
 * El cuerpo de creación se valida antes de delegar al servicio.
 * Las reglas de persistencia y conservación del rol ADMIN están en la capa de servicio.
 */
@RestController
@RequestMapping("/api/admin/teachers")
public class AdminTeacherController {
    private final TeacherService teachers;

    /**
     * Recibe el servicio que resuelve identidades y cambios de rol.
     * Las anotaciones del controlador restringen las rutas antes de delegar.
     */
    public AdminTeacherController(TeacherService teachers) {
        this.teachers = teachers;
    }

    /**
     * Entrega a ADMIN o JEFE las identidades disponibles para la administración.
     * Las vistas incluyen identificador, código, nombre y rol de todos los roles.
     * El resultado se ordena por código y la consulta no modifica docentes.
     */
    @GetMapping
    @PreAuthorize("@roleAccess.hasAnyRole(authentication, 'ADMIN', 'JEFE')")
    public List<TeacherView> list() {
        return teachers.list();
    }

    /**
     * Recibe un DTO validado con código de siete dígitos y requiere rol ADMIN.
     * Crea la identidad si no existe o promueve la existente a JEFE.
     * Una identidad ADMIN conserva su rol y la respuesta contiene la vista persistida.
     */
    @PostMapping
    @PreAuthorize("@roleAccess.hasRole(authentication, 'ADMIN')")
    public TeacherView createJefe(@Valid @RequestBody CreateJefeRequest request) {
        return teachers.createOrPromoteJefe(request.code());
    }
}