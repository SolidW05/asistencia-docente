package mx.udg.cut.asistencia.repositories.schedule;

import mx.udg.cut.asistencia.models.schedule.TeachingClass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingClassRepository extends JpaRepository<TeachingClass, UUID> {
    @EntityGraph(attributePaths = "allowedModules")
    List<TeachingClass> findByTeacherIdOrderByNameAsc(UUID teacherId);

    @EntityGraph(attributePaths = "allowedModules")
    List<TeachingClass> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "allowedModules")
    Optional<TeachingClass> findWithModulesById(UUID id);
}
