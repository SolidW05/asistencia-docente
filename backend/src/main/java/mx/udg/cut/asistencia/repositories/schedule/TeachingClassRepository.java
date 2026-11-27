package mx.udg.cut.asistencia.repositories.schedule;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.udg.cut.asistencia.models.schedule.TeachingClass;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta asignaciones de clase por docente o por creador administrativo.
 * Los EntityGraph cargan módulos permitidos junto con cada clase solicitada.
 * Las listas usan orden ascendente por nombre cuando el método lo indica.
 * La verificación del alcance se realiza en los servicios.
 */
public interface TeachingClassRepository extends JpaRepository<TeachingClass, UUID> {
    /**
     * Busca asignaciones del docente ordenadas por nombre.
     * El bloqueo comparte la lectura con otros docentes y coordina la creación de ocurrencias
     * con la edición del horario.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<TeachingClass> findByTeacherIdOrderByNameAscIdAsc(UUID teacherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TeachingClass c where c.id = :id")
    Optional<TeachingClass> findForUpdate(@Param("id") UUID id);

    /**
     * Lista las clases ordenadas por nombre con sus módulos permitidos.
     * No filtra por creador, docente ni estado.
     */
    @EntityGraph(attributePaths = "allowedModules")
    List<TeachingClass> findAllByOrderByNameAsc();

    /**
     * Restringe la lista al creador y ordena por nombre.
     * Carga también los módulos permitidos mediante EntityGraph.
     */
    @EntityGraph(attributePaths = "allowedModules")
    List<TeachingClass> findAllByCreatedByOrderByNameAsc(UUID createdBy);

    /**
     * Comprueba si una clase existe y fue creada por el identificador indicado.
     * Se usa para delimitar el alcance de un jefe.
     */
    boolean existsByIdAndCreatedBy(UUID id, UUID createdBy);

    /**
     * Busca por identificador cargando los módulos permitidos.
     * Devuelve Optional vacío si no existe la clase.
     */
    @EntityGraph(attributePaths = "allowedModules")
    Optional<TeachingClass> findWithModulesById(UUID id);
}
