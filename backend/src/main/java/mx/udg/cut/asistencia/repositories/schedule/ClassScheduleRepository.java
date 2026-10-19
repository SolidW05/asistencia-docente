package mx.udg.cut.asistencia.repositories.schedule;

import mx.udg.cut.asistencia.models.schedule.ClassSchedule;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, UUID> {
}
