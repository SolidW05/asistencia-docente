package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.geofence.GeofenceRequest;
import mx.udg.cut.asistencia.dto.geofence.GeofenceUpdateRequest;
import mx.udg.cut.asistencia.dto.geofence.GeofenceView;
import mx.udg.cut.asistencia.models.geofence.Geofence;
import mx.udg.cut.asistencia.services.geofence.GeofenceService;
import mx.udg.cut.asistencia.utils.CanonicalJson;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone consulta de geocercas y escritura administrativa restringida a ADMIN.
 * Los DTO de entrada se validan antes de delegar las reglas al servicio.
 * La conversión de salida interpreta el JSON geométrico almacenado.
 * El servicio realiza validación espacial y conserva las versiones históricas.
 */
@RestController
@RequestMapping("/api")
public class GeofenceController {
    private final GeofenceService service;
    private final CanonicalJson json;

    /**
     * Recibe el servicio espacial y el serializador usado para interpretar geometría.
     * El servicio conserva las reglas y versiones de geocerca.
     */
    public GeofenceController(GeofenceService service, CanonicalJson json) {
        this.service = service;
        this.json = json;
    }

    /**
     * Devuelve todas las geocercas ordenadas por nombre, incluidas las inactivas.
     * Cada DTO contiene campus, tipo, padre, geometría JSON, actividad y versión.
     * La consulta no modifica las geometrías ni sus versiones.
     */
    @GetMapping("/geofences")
    public List<GeofenceView> list() {
        return service.list().stream().map(this::view).toList();
    }

    /**
     * Recibe un DTO validado con campus, nombre, tipo, padre, geometría y actividad.
     * Solo ADMIN puede crear; el servicio valida la jerarquía y el polígono.
     * Devuelve la nueva geocerca como DTO con su versión persistida.
     */
    @PostMapping("/admin/geofences")
    @PreAuthorize("hasRole('ADMIN')")
    public GeofenceView create(@Valid @RequestBody GeofenceRequest r) {
        return view(
                service.create(
                        r.campusId(), r.name(), r.type(), r.parentId(), r.geometry(), r.active()));
    }

    /**
     * Recibe UUID, geometría y demás campos junto con la versión esperada.
     * Solo ADMIN puede editar; una versión obsoleta produce conflicto.
     * El servicio valida jerarquía y geometría y devuelve la vista actualizada.
     */
    @PutMapping("/admin/geofences/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GeofenceView update(@PathVariable UUID id, @Valid @RequestBody GeofenceUpdateRequest r) {
        return view(
                service.update(
                        id,
                        r.campusId(),
                        r.name(),
                        r.type(),
                        r.parentId(),
                        r.geometry(),
                        r.active(),
                        r.version()));
    }

    /**
     * Convierte la entidad persistida en DTO e interpreta su geometría JSON.
     * Incluye la versión y el estado activo sin modificar la entidad.
     */
    private GeofenceView view(Geofence g) {
        return new GeofenceView(
                g.getId(),
                g.getCampusId(),
                g.getName(),
                g.getType(),
                g.getParentId(),
                json.read(g.getGeometryJson()),
                g.isActive(),
                g.getVersion());
    }
}
