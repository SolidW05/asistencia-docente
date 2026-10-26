package mx.udg.cut.asistencia.repositories.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {
    List<AttendanceRecord> findAllByOrderByReceivedAtDesc();

    Optional<AttendanceRecord> findByIdAndTeacherId(UUID id, UUID teacherId);

    @Query("select a.teacherId from AttendanceRecord a where a.id = :attendanceId")
    Optional<UUID> findTeacherIdById(@Param("attendanceId") UUID attendanceId);


    @Query(
            value = """
                    select a.* from attendances a
                    join class_occurrences o on o.id = a.occurrence_id
                    join teaching_classes c on c.id = o.class_id
                    where c.created_by = :creatorId
                    order by a.received_at desc
                    """,
            nativeQuery = true)
    List<AttendanceRecord> findByClassCreatorOrderByReceivedAtDesc(
            @Param("creatorId") UUID creatorId);

    List<AttendanceRecord> findByTeacherIdOrderByReceivedAtDesc(UUID teacherId);

    Optional<AttendanceRecord> findByTeacherIdAndIdempotencyKey(
            UUID teacherId, UUID idempotencyKey);

    Optional<AttendanceRecord> findByTeacherIdAndOccurrenceId(UUID teacherId, UUID occurrenceId);
}
