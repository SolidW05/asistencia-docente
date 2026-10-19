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

@RestController
public class CampusController {
    private final CampusService service;

    public CampusController(CampusService service) {
        this.service = service;
    }

    @GetMapping("/api/campuses")
    public List<CampusView> campuses() {
        return service.list();
    }

    @PostMapping("/api/admin/campuses")
    @PreAuthorize("hasRole('ADMIN')")
    public CampusView create(@Valid @RequestBody CampusRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/admin/campuses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CampusView update(@PathVariable UUID id, @Valid @RequestBody CampusRequest request) {
        return service.update(id, request);
    }
}
