package mx.udg.cut.asistencia.models.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea un bloque persistido: id identifica el evento, index fija su orden y serverAt su fecha.
 * Payload conserva la evidencia; previousHash enlaza al bloque anterior y hash sella el contenido.
 * Los demás campos identifican la cadena, el tipo de evento, docente, asistencia y formato.
 */
@Entity
@Table(name = "global_chain_blocks")
public class ChainBlock {
    @Id
    private UUID id;

    @Column(name = "chain_id", nullable = false, length = 32)
    private String chainId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "block_index", nullable = false)
    private long index;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "attendance_id")
    private UUID attendanceId;

    @Column(name = "server_at")
    private Instant serverAt;

    @Column(nullable = false)
    private String payload;

    @Column(name = "previous_hash", nullable = false)
    private String previousHash;

    @Column(nullable = false)
    private String hash;

    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    protected ChainBlock() {
    }
}
