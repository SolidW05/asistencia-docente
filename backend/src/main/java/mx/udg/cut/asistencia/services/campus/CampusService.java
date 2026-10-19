package mx.udg.cut.asistencia.services.campus;

import java.util.List;
import java.util.UUID;
import mx.udg.cut.asistencia.dto.campus.CampusRequest;
import mx.udg.cut.asistencia.dto.campus.CampusView;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.models.campus.Campus;
import mx.udg.cut.asistencia.repositories.campus.CampusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusService {
    private final CampusRepository campuses;

    public CampusService(CampusRepository campuses) {
        this.campuses = campuses;
    }

    @Transactional(readOnly = true)
    public List<CampusView> list() {
        return campuses.findAllByOrderByNameAsc().stream().map(CampusView::from).toList();
    }

    @Transactional
    public CampusView create(CampusRequest request) {
        return CampusView.from(
                campuses.save(new Campus(UUID.randomUUID(), request.name(), request.active())));
    }

    @Transactional
    public CampusView update(UUID id, CampusRequest request) {
        Campus campus =
                campuses.findById(id)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "CAMPUS_NOT_FOUND", "No se encontró el campus"));
        campus.update(request.name(), request.active());
        return CampusView.from(campuses.save(campus));
    }
}
