package mx.udg.cut.asistencia.repositories.schedule;

import mx.udg.cut.asistencia.models.schedule.ClassSchedule;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Expone las operaciones JPA heredadas para horarios regulares.
 * La clave identifica la clase cuyo día y franja se almacenan.
 * El servicio de clases coordina estas filas con las ocurrencias concretas.
 * No define consultas adicionales ni aplica autorización.
 */
public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, UUID> {
}
