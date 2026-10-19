package mx.udg.cut.asistencia.repositories.schedule;

import mx.udg.cut.asistencia.models.schedule.ClassOccurrence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassOccurrenceRepository extends JpaRepository<ClassOccurrence, UUID> {
    Optional<ClassOccurrence> findByClassIdAndDate(UUID classId, LocalDate date);

    Optional<ClassOccurrence> findByIdAndTeacherId(UUID id, UUID teacherId);
}
