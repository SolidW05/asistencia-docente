package mx.udg.cut.asistencia.services.chain;

import static mx.udg.cut.asistencia.models.chain.ChainDefinition.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import mx.udg.cut.asistencia.utils.CanonicalJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Inicializa el bloque 0 y su cabecera una sola vez, sin crear datos de demostración. */
@Service
public class GenesisService {
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    private final Clock clock;

    public GenesisService(JdbcTemplate jdbc, CanonicalJson json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** Se llama bajo la transacción y el bloqueo de DatabaseInitializationConfig. */
    public void initialize() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "El Génesis debe inicializarse dentro de la transacción de arranque.");
        if (!GENESIS_HASH.equals(json.sha256(GENESIS_PHRASE)))
            throw new IllegalStateException("El hash del Génesis no coincide con su frase.");
        boolean hasHead =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "select exists(select 1 from global_chain_head where id=1)",
                                Boolean.class));
        if (hasHead)
            return;
        if (jdbc.queryForObject("select count(*) from global_chain_blocks", Long.class) != 0
                || jdbc.queryForObject("select count(*) from attendances", Long.class) != 0)
            throw new IllegalStateException(
                    "La cadena tiene registros pero carece de cabecera; no se puede reinicializar.");

        jdbc.update(
                """
                insert into global_chain_blocks
                    (id,chain_id,block_index,event_type,teacher_id,attendance_id,server_at,payload,previous_hash,hash,format_version)
                values (?,?,0,'GENESIS',null,null,null,?,?,?,?)
                """,
                GENESIS_BLOCK_ID,
                CHAIN_ID,
                json.write(Map.of("phrase", GENESIS_PHRASE)),
                GENESIS_PREVIOUS_HASH,
                GENESIS_HASH,
                FORMAT_VERSION);
        jdbc.update(
                """
                insert into global_chain_head(id,chain_id,last_index,last_hash,total_blocks,updated_at)
                values (1,?,0,?,1,?)
                """,
                CHAIN_ID,
                GENESIS_HASH,
                Timestamp.from(clock.instant()));
    }
}
