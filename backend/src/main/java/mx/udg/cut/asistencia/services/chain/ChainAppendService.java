package mx.udg.cut.asistencia.services.chain;

import static mx.udg.cut.asistencia.models.chain.ChainDefinition.*;

import mx.udg.cut.asistencia.repositories.teacher.TeacherRepository;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.utils.CanonicalJson;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agrega eventos a la cadena global dentro de la transacción de asistencia.
 * Bloquea docente y cabecera para ordenar escrituras de distintos actores.
 * Comprueba enlaces y hashes existentes antes de construir el nuevo bloque.
 * JDBC permite mantener el bloqueo, inserción y actualización de cabecera juntos.
 */
@Service
public class ChainAppendService {
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    private final TeacherRepository teachers;

    /**
     * Recibe JDBC, JSON canónico y repositorio de docentes.
     * Estas dependencias permiten serializar escrituras y calcular hashes dentro de la transacción externa.
     */
    public ChainAppendService(JdbcTemplate jdbc, CanonicalJson json, TeacherRepository teachers) {
        this.jdbc = jdbc;
        this.json = json;
        this.teachers = teachers;
    }

    /**
     * Añade un evento inicial de asistencia con la evidencia recibida.
     * Exige una transacción existente para que asistencia y bloque se confirmen juntos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID teacherId, UUID attendanceId, Instant serverAt, String payload) {
        appendEvent(teacherId, attendanceId, serverAt, "ATTENDANCE_RECORDED", payload);
    }

    /**
     * Añade el evento de cambio de estado de la asistencia indicada.
     * Participa obligatoriamente en la transacción que realiza la revisión.
     */
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

    /**
     * Bloquea al docente y después la cabecera global para serializar escritores.
     * Comprueba la evidencia existente y toma el siguiente índice y hash anterior.
     * Calcula SHA-256 del sobre canónico, inserta el bloque y actualiza la cabecera.
     * Un fallo provoca que la transacción que llamó al servicio no confirme la escritura.
     */
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
            throw ApiException.conflict("CHAIN_INVALID", "No existe la cabecera de GLOBAL_ATTENDANCE.");
        // El mismo bloqueo protege la validación previa y el cálculo del siguiente enlace.
        verifyStoredLinks(heads.getFirst());
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
        // La cabecera se actualiza después de insertar el bloque, dentro de la misma transacción.
        jdbc.update(
                """
            update global_chain_head set last_index=?,last_hash=?,total_blocks=?,updated_at=? where id=1
            """,
                index,
                hash,
                heads.getFirst().totalBlocks() + 1,
                Timestamp.from(serverAt));
    }

    /**
     * Recorre los bloques ordenados bajo el bloqueo de cabecera antes de agregar otro.
     * Comprueba el ancla génesis, índices, tipo, metadatos, enlaces y hashes recalculados.
     * Contrasta último hash, índice y cantidad con la cabecera; reporta conflicto si hay alteración.
     * No compara estados de asistencia porque la transacción actual puede tener cambios pendientes.
     */
    private void verifyStoredLinks(Head head) {
        List<StoredBlock> blocks = jdbc.query("""
                select id,chain_id,block_index,event_type,teacher_id,attendance_id,server_at,payload,previous_hash,hash,format_version
                from global_chain_blocks order by block_index
                """, (rs, row) -> new StoredBlock(rs.getObject("id", UUID.class), rs.getString("chain_id"),
                rs.getLong("block_index"), rs.getString("event_type"), rs.getObject("teacher_id", UUID.class),
                rs.getObject("attendance_id", UUID.class), rs.getTimestamp("server_at"), rs.getString("payload"),
                rs.getString("previous_hash"), rs.getString("hash"), rs.getInt("format_version")));
        if (blocks.isEmpty()) throw ApiException.conflict("CHAIN_INVALID", "Falta el génesis de la cadena.");
        StoredBlock genesis = blocks.getFirst();
        if (!GENESIS_BLOCK_ID.equals(genesis.id()) || genesis.index() != 0 || !CHAIN_ID.equals(genesis.chainId())
                || !"GENESIS".equals(genesis.eventType()) || genesis.teacherId() != null || genesis.attendanceId() != null
                || genesis.serverAt() != null || genesis.version() != FORMAT_VERSION
                || !GENESIS_PREVIOUS_HASH.equals(genesis.previous()) || !GENESIS_HASH.equals(genesis.hash())
                || !json.write(java.util.Map.of("phrase", GENESIS_PHRASE)).equals(genesis.payload()))
            throw ApiException.conflict("CHAIN_INVALID", "El génesis de la cadena fue alterado.");
        String previous = GENESIS_HASH;
        for (int i = 1; i < blocks.size(); i++) {
            StoredBlock block = blocks.get(i);
            if (!CHAIN_ID.equals(block.chainId()) || block.index() != i || block.version() != FORMAT_VERSION
                    || block.id() == null || block.teacherId() == null || block.attendanceId() == null || block.serverAt() == null
                    || !("ATTENDANCE_RECORDED".equals(block.eventType()) || "ATTENDANCE_STATUS_CHANGED".equals(block.eventType()))
                    || !previous.equals(block.previous()))
                throw ApiException.conflict("CHAIN_INVALID", "Los enlaces del bloque " + i + " fueron alterados.");
            String computed;
            try {
                computed = json.hashGlobalBlock(block.chainId(), block.teacherId(), block.id(), block.index(),
                        block.eventType(), block.attendanceId(), block.serverAt().toInstant(), block.previous(), block.payload(), block.version());
            } catch (IllegalArgumentException ex) {
                throw ApiException.conflict("CHAIN_INVALID", "El payload del bloque " + i + " no es JSON válido.");
            }
            if (!computed.equals(block.hash())) throw ApiException.conflict("CHAIN_INVALID", "El hash del bloque " + i + " fue alterado.");
            previous = block.hash();
        }
        if (head.totalBlocks() != blocks.size() || head.lastIndex() != blocks.size() - 1 || !previous.equals(head.lastHash()))
            throw ApiException.conflict("CHAIN_INVALID", "La cabecera de la cadena fue alterada.");
    }

    /**
     * Agrupa las columnas leídas de un bloque para validar su sobre y enlace.
     * Mantiene Timestamp de JDBC hasta convertirlo al recalcular el hash.
     */
    private record StoredBlock(UUID id, String chainId, long index, String eventType, UUID teacherId,
            UUID attendanceId, Timestamp serverAt, String payload, String previous, String hash, int version) {}

    /**
     * Agrupa cadena, índice final, hash final y cantidad bajo el bloqueo de escritura.
     * Sus valores determinan el nuevo enlace y permiten comprobar la cabecera.
     */
    private record Head(String chainId, long lastIndex, String lastHash, long totalBlocks) {
    }
}
