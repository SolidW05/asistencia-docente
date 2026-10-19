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

@RestController
@RequestMapping("/api")
public class GeofenceController {
    private final GeofenceService service;
    private final CanonicalJson json;

    public GeofenceController(GeofenceService service, CanonicalJson json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping("/geofences")
    public List<GeofenceView> list() {
        return service.list().stream().map(this::view).toList();
    }

    @PostMapping("/admin/geofences")
    @PreAuthorize("hasRole('ADMIN')")
    public GeofenceView create(@Valid @RequestBody GeofenceRequest r) {
        return view(
                service.create(
                        r.campusId(), r.name(), r.type(), r.parentId(), r.geometry(), r.active()));
    }

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
