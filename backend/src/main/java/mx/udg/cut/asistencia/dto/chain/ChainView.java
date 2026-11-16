package mx.udg.cut.asistencia.dto.chain;

import java.util.List;
import java.util.UUID;

/** Reúne la cabecera y los bloques ordenados de la cadena global. */
public record ChainView(
        String chainId,
        UUID teacherId,
        long lastIndex,
        String lastHash,
        long totalBlocks,
        String genesisPhrase,
        String genesisHash,
        List<BlockView> blocks
) {
}
