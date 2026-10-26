package mx.udg.cut.asistencia.services.chain;

import static mx.udg.cut.asistencia.models.chain.ChainDefinition.*;

import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.utils.CanonicalJson;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Agrega eventos inmutables a la cadena global dentro de la transacción de asistencia. */
@Service
public class ChainAppendService {
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    private final TeacherRepository teachers;

    public ChainAppendService(JdbcTemplate jdbc, CanonicalJson json, TeacherRepository teachers) {
        this.jdbc = jdbc;
        this.json = json;
        this.teachers = teachers;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID teacherId, UUID attendanceId, Instant serverAt, String payload) {
        appendEvent(teacherId, attendanceId, serverAt, "ATTENDANCE_RECORDED", payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendStatusChanged(
            UUID attendanceOwnerId, UUID attendanceId, Instant changedAt, String payload) {
        appendEvent(
                attendanceOwnerId,
                attendanceId,
                changedAt,
                "ATTENDANCE_STATUS_CHANGED",
                payload);
    }

    private void appendEvent(
            UUID attendanceOwnerId,
            UUID attendanceId,
            Instant serverAt,
            String eventType,
            String payload) {
        // El bloqueo del docente serializa sus asistencias; la cabecera global ordena a los distintos docentes.
        teachers.lockById(attendanceOwnerId).orElseThrow();
        List<Head> heads =
                jdbc.query(
                        "select chain_id,last_index,last_hash,total_blocks from global_chain_head where id=1 for update",
                        (rs, row) ->
                                new Head(
                                        rs.getString("chain_id"),
                                        rs.getLong("last_index"),
                                        rs.getString("last_hash"),
                                        rs.getLong("total_blocks")));
        if (heads.isEmpty() || !CHAIN_ID.equals(heads.getFirst().chainId()))
            throw new IllegalStateException("No existe la cabecera de GLOBAL_ATTENDANCE.");
        long index = heads.getFirst().lastIndex() + 1;
        String previous = heads.getFirst().lastHash();
        UUID blockId = UUID.randomUUID();
        String hash =
                json.hashGlobalBlock(
                        CHAIN_ID,
                        attendanceOwnerId,
                        blockId,
                        index,
                        eventType,
                        attendanceId,
                        serverAt,
                        previous,
                        payload,
                        FORMAT_VERSION);
        jdbc.update(
                """
            insert into global_chain_blocks(id,chain_id,block_index,event_type,teacher_id,attendance_id,server_at,payload,previous_hash,hash,format_version)
            values(?,?,?,?,?,?,?,?,?,?,?)
            """,
                blockId,
                CHAIN_ID,
                index,
                eventType,
                attendanceOwnerId,
                attendanceId,
                Timestamp.from(serverAt),
                payload,
                previous,
                hash,
                FORMAT_VERSION);
        jdbc.update(
                """
            update global_chain_head set last_index=?,last_hash=?,total_blocks=?,updated_at=? where id=1
            """,
                index,
                hash,
                heads.getFirst().totalBlocks() + 1,
                Timestamp.from(serverAt));
    }

    private record Head(String chainId, long lastIndex, String lastHash, long totalBlocks) {
    }
}
