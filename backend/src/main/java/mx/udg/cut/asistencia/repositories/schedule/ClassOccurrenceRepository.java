package mx.udg.cut.asistencia.repositories.schedule;

import mx.udg.cut.asistencia.models.schedule.ClassOccurrence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accede a sesiones concretas de una clase y su docente.
 * Las consultas derivadas delimitan por clase y fecha o por identificador y propietario.
 * El servicio utiliza estos resultados para validar registros de asistencia.
 * Un Optional vacío expresa que no hay una coincidencia.
 */
public interface ClassOccurrenceRepository extends JpaRepository<ClassOccurrence, UUID> {
    /**
     * Busca la ocurrencia de una clase para una fecha local concreta.
     * Devuelve Optional vacío si aún no existe.
     */
    Optional<ClassOccurrence> findByClassIdAndDate(UUID classId, LocalDate date);

    /**
     * Busca una ocurrencia solo cuando corresponde al docente indicado.
     * El resultado vacío también cubre la ausencia de la ocurrencia.
     */
    Optional<ClassOccurrence> findByIdAndTeacherId(UUID id, UUID teacherId);
}
