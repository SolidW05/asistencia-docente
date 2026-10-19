package mx.udg.cut.asistencia.repositories.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {
    List<AttendanceRecord> findByTeacherIdOrderByReceivedAtDesc(UUID teacherId);

    Optional<AttendanceRecord> findByTeacherIdAndIdempotencyKey(
            UUID teacherId, UUID idempotencyKey);

    Optional<AttendanceRecord> findByTeacherIdAndOccurrenceId(UUID teacherId, UUID occurrenceId);
}
