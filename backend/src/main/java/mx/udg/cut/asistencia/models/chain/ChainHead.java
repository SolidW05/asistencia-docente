package mx.udg.cut.asistencia.models.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

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
