package mx.udg.cut.asistencia.repositories.geofence;

import mx.udg.cut.asistencia.models.geofence.Geofence;
import mx.udg.cut.asistencia.models.geofence.GeofenceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {
    List<Geofence> findAllByOrderByNameAsc();

    List<Geofence> findByCampusIdOrderByNameAsc(UUID campusId);

    Optional<Geofence> findFirstByCampusIdAndType(UUID campusId, GeofenceType type);

    List<Geofence> findByParentId(UUID parentId);

    List<Geofence> findByCampusIdAndTypeAndActiveTrue(UUID campusId, GeofenceType type);
}
