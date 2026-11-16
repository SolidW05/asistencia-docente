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

/**
 * Coordina la consulta y modificación de campus mediante JPA.
 * Devuelve DTO de salida en lugar de exponer directamente las entidades.
 * Cada escritura se realiza dentro de una transacción del servicio.
 * La actualización informa cuando el campus solicitado no existe.
 */
@Service
public class CampusService {
    private final CampusRepository campuses;

    /**
     * Recibe el repositorio que consulta y persiste las entidades de campus.
     * El servicio transforma sus resultados en vistas para la API.
     */
    public CampusService(CampusRepository campuses) {
        this.campuses = campuses;
    }

    /**
     * Consulta campus en orden ascendente de nombre y los convierte en vistas.
     * La transacción es de solo lectura.
     */
    @Transactional(readOnly = true)
    public List<CampusView> list() {
        return campuses.findAllByOrderByNameAsc().stream().map(CampusView::from).toList();
    }

    /**
     * Crea una entidad con UUID nuevo y los datos del DTO recibido.
     * La guarda dentro de la transacción y devuelve su vista.
     */
    @Transactional
    public CampusView create(CampusRequest request) {
        return CampusView.from(
                campuses.save(new Campus(UUID.randomUUID(), request.name(), request.active())));
    }

    /**
     * Busca el campus por UUID y responde con error si no existe.
     * Actualiza nombre y actividad, guarda la entidad y devuelve la vista.
     */
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
