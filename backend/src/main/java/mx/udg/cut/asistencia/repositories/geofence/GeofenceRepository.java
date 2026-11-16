package mx.udg.cut.asistencia.repositories.geofence;

import mx.udg.cut.asistencia.models.geofence.Geofence;
import mx.udg.cut.asistencia.models.geofence.GeofenceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Consulta las geocercas persistidas por campus, tipo y relación jerárquica.
 * Spring Data deriva los filtros indicados en los nombres de método.
 * Las reglas geométricas y la conservación de versiones pertenecen al servicio.
 * Las listas vacías indican que no existen coincidencias.
 */
public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {
    /**
     * Lista las geocercas ordenadas por nombre ascendente.
     * Incluye las que están inactivas.
     */
    List<Geofence> findAllByOrderByNameAsc();

    /**
     * Restringe las geocercas al campus y las ordena por nombre.
     * La consulta no filtra por tipo ni actividad.
     */
    List<Geofence> findByCampusIdOrderByNameAsc(UUID campusId);

    /**
     * Busca la primera coincidencia de campus y tipo.
     * El Optional queda vacío si no hay geocerca coincidente.
     */
    Optional<Geofence> findFirstByCampusIdAndType(UUID campusId, GeofenceType type);

    /**
     * Lista geocercas que referencian el identificador padre indicado.
     * No aplica filtro de actividad ni garantiza orden.
     */
    List<Geofence> findByParentId(UUID parentId);

    /**
     * Lista únicamente geocercas activas del campus y tipo indicados.
     * No impone un orden en el resultado.
     */
    List<Geofence> findByCampusIdAndTypeAndActiveTrue(UUID campusId, GeofenceType type);
}
