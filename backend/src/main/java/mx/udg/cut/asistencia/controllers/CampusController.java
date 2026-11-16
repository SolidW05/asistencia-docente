package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.campus.CampusRequest;
import mx.udg.cut.asistencia.dto.campus.CampusView;
import mx.udg.cut.asistencia.services.campus.CampusService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone consulta de campus y operaciones administrativas de modificación.
 * Las solicitudes de escritura se validan y requieren el rol ADMIN declarado en las rutas.
 * El servicio realiza la persistencia y comunica si un identificador no existe.
 * El controlador no calcula reglas de geocercas.
 */
@RestController
public class CampusController {
    private final CampusService service;

    /**
     * Recibe el servicio encargado de consultar y persistir campus.
     * Las rutas delegan en él después de aplicar sus restricciones HTTP.
     */
    public CampusController(CampusService service) {
        this.service = service;
    }

    /**
     * Devuelve vistas de todos los campus, activos e inactivos, ordenados por nombre.
     * Cada vista incluye los datos del campus persistido.
     * La consulta no crea ni modifica campus o geocercas.
     */
    @GetMapping("/api/campuses")
    public List<CampusView> campuses() {
        return service.list();
    }

    /**
     * Recibe nombre y estado activo en un DTO validado y exige ADMIN.
     * Crea un campus con UUID nuevo y devuelve su CampusView persistida.
     * Esta operación no crea automáticamente una geocerca.
     */
    @PostMapping("/api/admin/campuses")
    @PreAuthorize("hasRole('ADMIN')")
    public CampusView create(@Valid @RequestBody CampusRequest request) {
        return service.create(request);
    }

    /**
     * Recibe el UUID de la ruta y el DTO validado con nombre y actividad.
     * Exige ADMIN y devuelve la vista del campus actualizado.
     * Si el identificador no corresponde a una fila, informa que no se encontró.
     */
    @PutMapping("/api/admin/campuses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CampusView update(@PathVariable UUID id, @Valid @RequestBody CampusRequest request) {
        return service.update(id, request);
    }
}
