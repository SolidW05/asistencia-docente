package mx.udg.cut.asistencia.services.chain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.utils.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ofrece una simulación reversible sobre un payload persistido de la cadena.
 * Solo está habilitada cuando coinciden perfil demo y bandera explícita.
 * Bloquea la cabecera, guarda respaldo transaccional y deja intacto el hash.
 * La restauración compara el contenido alterado antes de recuperar el original.
 */
@Service
public class ChainAttackService {
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;
    private final ChainService chain;
    private final boolean enabled;

    /**
     * Recibe JDBC, JSON, verificador y configuración de entorno.
     * Solo habilita la simulación si el perfil demo y la bandera explícita están activos.
     */
    public ChainAttackService(
            JdbcTemplate jdbc,
            CanonicalJson json,
            ChainService chain,
            Environment environment,
            @Value("${app.chain-attack-enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.json = json;
        this.chain = chain;
        this.enabled = enabled && environment.acceptsProfiles(Profiles.of("demo"));
    }

    /**
     * Comunica disponibilidad de la simulación y UUID del bloque alterado pendiente.
     * Un identificador nulo indica que no hay respaldo pendiente visible.
     */
    public record AttackStatus(boolean enabled, UUID affectedBlockId) {}

    /**
     * Agrupa UUID, payload original y payload alterado guardados por la simulación.
     * Se utiliza para restaurar sin confiar en valores enviados por la interfaz.
     */
    private record Backup(UUID blockId, String original, String altered) {}

    /**
     * Si la función está deshabilitada devuelve ese estado sin consultar respaldos.
     * Si está habilitada informa el bloque pendiente, también después de reiniciar.
     */
    @Transactional(readOnly = true)
    public AttackStatus status() {
        if (!enabled) return new AttackStatus(false, null);
        List<Backup> backups = backups();
        return new AttackStatus(true, backups.isEmpty() ? null : backups.getFirst().blockId());
    }

    /**
     * Exige habilitación y bloquea la cabecera para coordinarse con escritores.
     * Rechaza otra simulación pendiente o una cadena ya inconsistente.
     * Excluye el génesis, agrega una marca al JSON y guarda original y alteración juntos.
     * No modifica hash ni cabecera, de modo que la verificación detecta el cambio.
     */
    @Transactional
    public AttackStatus attack(UUID blockId) {
        requireEnabled();
        lockHead();
        if (!backups().isEmpty())
            throw ApiException.conflict("ATTACK_PENDING", "Restaura primero la simulación anterior.");
        if (!chain.verify(null).valid())
            throw ApiException.conflict("CHAIN_INVALID", "La cadena ya presenta una inconsistencia.");
        List<String> payloads =
                jdbc.query(
                        "select payload from global_chain_blocks where id=? and block_index>0",
                        (rs, row) -> rs.getString(1),
                        blockId);
        if (payloads.isEmpty())
            throw ApiException.notFound(
                    "BLOCK_NOT_FOUND",
                    "Selecciona un bloque de asistencia existente; no se modifica el génesis.");
        String original = payloads.getFirst();
        var parsed = json.read(original);
        if (!(parsed instanceof ObjectNode object))
            throw ApiException.conflict("INVALID_PAYLOAD", "El payload no es un objeto JSON.");
        object.put("_simulacionAtaque", "Contenido alterado deliberadamente");
        String altered = json.write(object);
        if (original.equals(altered))
            throw ApiException.conflict("ALREADY_ALTERED", "El contenido ya contiene esta marca.");
        // Respaldo y alteración se confirman juntos; no se actualiza el hash del bloque.
        jdbc.update(
                "insert into chain_attack_backup(block_id,original_payload,altered_payload) values (?,?,?)",
                blockId, original, altered);
        jdbc.update("update global_chain_blocks set payload=? where id=?", altered, blockId);
        return new AttackStatus(true, blockId);
    }

    /**
     * Exige habilitación y bloqueo y busca el respaldo pendiente.
     * Actualiza solo si el payload sigue exactamente como lo dejó la simulación.
     * Elimina el respaldo después de restaurar; una modificación posterior provoca conflicto.
     */
    @Transactional
    public AttackStatus restore() {
        requireEnabled();
        lockHead();
        List<Backup> backups = backups();
        if (backups.isEmpty())
            throw ApiException.conflict("NO_ATTACK", "No hay simulación pendiente de restaurar.");
        Backup backup = backups.getFirst();
        int updated =
                jdbc.update(
                        "update global_chain_blocks set payload=? where id=? and payload=?",
                        backup.original(), backup.blockId(), backup.altered());
        if (updated != 1)
            throw ApiException.conflict(
                    "BLOCK_CHANGED",
                    "El bloque cambió después de la simulación; no se sobrescribió.");
        jdbc.update("delete from chain_attack_backup where block_id=?", backup.blockId());
        return new AttackStatus(true, null);
    }

    /**
     * Comprueba la bandera calculada al construir el servicio.
     * Si perfil o configuración no habilitan la función, rechaza la operación.
     */
    private void requireEnabled() {
        if (!enabled)
            throw ApiException.forbidden("ATTACK_DISABLED", "La simulación está deshabilitada.");
    }

    /**
     * Bloquea la fila de cabecera global hasta terminar la transacción actual.
     * Coordina la simulación y restauración con el agregado normal de bloques.
     */
    private void lockHead() {
        jdbc.queryForObject("select id from global_chain_head where id=1 for update", Short.class);
    }

    /**
     * Lee los respaldos persistidos y los transforma en registros internos.
     * No modifica su contenido ni depende de datos enviados por el navegador.
     */
    private List<Backup> backups() {
        return jdbc.query(
                "select block_id,original_payload,altered_payload from chain_attack_backup",
                (rs, row) -> new Backup(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
    }
}
