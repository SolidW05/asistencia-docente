package mx.udg.cut.asistencia.services.chain;

import static mx.udg.cut.asistencia.models.chain.ChainDefinition.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import mx.udg.cut.asistencia.utils.CanonicalJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Inicializa el ancla que proporciona el hash anterior al primer registro.
 * Debe ejecutarse dentro de la transacción y bloqueo de inicialización del esquema.
 * Si ya hay cabecera no crea otra; si hay datos sin cabecera rechaza reinicializar.
 * El bloque inicial y su cabecera se insertan juntos mediante JDBC.
 */
@Service
public class GenesisService {
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    private final Clock clock;

    /**
     * Recibe JDBC, JSON canónico y reloj de aplicación.
     * Se utilizan durante la inicialización transaccional del ancla y la cabecera.
     */
    public GenesisService(JdbcTemplate jdbc, CanonicalJson json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Exige una transacción activa y comprueba el hash esperado de la frase inicial.
     * Si ya hay cabecera termina sin insertar otra.
     * Si hay bloques o asistencias sin cabecera rechaza reinicializar para no ocultar datos.
     * En una base vacía inserta el ancla y la cabecera inicial juntas.
     */
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
