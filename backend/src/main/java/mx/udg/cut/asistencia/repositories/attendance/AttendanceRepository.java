package mx.udg.cut.asistencia.repositories.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta asistencias por propietario, ocurrencia y clave idempotente.
 * Las consultas derivadas las interpreta Spring Data; la consulta por creador usa SQL nativo.
 * La unión con ocurrencias y clases permite delimitar el historial administrativo.
 * El servicio decide la autorización antes de solicitar estos datos.
 */
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {
    /**
     * Lista todas las asistencias con la recepción más reciente primero.
     * No aplica filtros de docente ni de creador.
     */
    List<AttendanceRecord> findAllByOrderByReceivedAtDesc();

    /**
     * Busca una asistencia solo si pertenece al docente indicado.
     * Un Optional vacío incluye tanto inexistencia como falta de coincidencia de propietario.
     */
    Optional<AttendanceRecord> findByIdAndTeacherId(UUID id, UUID teacherId);

    /**
     * La consulta JPQL devuelve únicamente el UUID del propietario.
     * El resultado está vacío si no existe la asistencia.
     */
    @Query("select a.teacherId from AttendanceRecord a where a.id = :attendanceId")
    Optional<UUID> findTeacherIdById(@Param("attendanceId") UUID attendanceId);


    /**
     * El SQL nativo une asistencia, ocurrencia y clase por sus claves.
     * Filtra por creador de la clase y ordena por recepción descendente.
     */
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

    /**
     * Restringe los registros al docente y los ordena por recepción descendente.
     * No incluye asistencias de otros propietarios.
     */
    List<AttendanceRecord> findByTeacherIdOrderByReceivedAtDesc(UUID teacherId);

    /**
     * Busca la solicitud previa del docente con la misma clave idempotente.
     * Devuelve vacío cuando esa combinación todavía no está registrada.
     */
    Optional<AttendanceRecord> findByTeacherIdAndIdempotencyKey(
            UUID teacherId, UUID idempotencyKey);

    /**
     * Busca el registro del docente para una ocurrencia concreta.
     * Un resultado vacío indica que no existe esa combinación.
     */
    Optional<AttendanceRecord> findByTeacherIdAndOccurrenceId(UUID teacherId, UUID occurrenceId);
}
