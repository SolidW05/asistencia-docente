package mx.udg.cut.asistencia.models.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cabecera única con índice, hash y total del último estado conocido de la cadena.
 * Facilita y serializa las escrituras; la verificación la contrasta con los bloques reales.
 */
@Entity
@Table(name = "global_chain_head")
public class ChainHead {
    @Id
    private Short id;

    @Column(name = "chain_id", nullable = false, length = 32)
    private String chainId;

    @Column(name = "last_index", nullable = false)
    private long lastIndex;

    @Column(name = "last_hash", nullable = false)
    private String lastHash;

    @Column(name = "total_blocks", nullable = false)
    private long totalBlocks;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChainHead() {
    }
}
