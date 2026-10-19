package mx.udg.cut.asistencia.services.chain;

import mx.udg.cut.asistencia.models.chain.ChainDefinition;

import mx.udg.cut.asistencia.dto.chain.BlockView;
import mx.udg.cut.asistencia.dto.chain.ChainView;
import mx.udg.cut.asistencia.dto.chain.VerifyView;
import mx.udg.cut.asistencia.utils.CanonicalJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChainService {
    private final JdbcTemplate jdbc;
    private final CanonicalJson json;

    public ChainService(JdbcTemplate jdbc, CanonicalJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChainView chain(UUID teacherId) {
        GlobalHead head = readHead();
        List<BlockView> visible = readVisible(teacherId).stream().map(this::view).toList();
        if (head == null)
            return new ChainView(
                    ChainDefinition.CHAIN_ID,
                    teacherId,
                    -1,
                    ChainDefinition.GENESIS_PREVIOUS_HASH,
                    0,
                    ChainDefinition.GENESIS_PHRASE,
                    ChainDefinition.GENESIS_HASH,
                    visible);
        return new ChainView(
                head.chainId(),
                teacherId,
                head.lastIndex(),
                head.lastHash(),
                head.totalBlocks(),
                ChainDefinition.GENESIS_PHRASE,
                ChainDefinition.GENESIS_HASH,
                visible);
    }

    /** Verifica la cadena global completa, pero nunca devuelve el payload de otro docente. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VerifyView verify(UUID requestingTeacherId) {
        List<BlockRaw> blocks = readAll();
        String error = null;
        int checked = 0;
        if (blocks.isEmpty())
            error = "Falta el bloque génesis de la cadena global.";
        if (error == null) {
            BlockRaw genesis = blocks.getFirst();
            checked++;
            error = genesisError(genesis);
        }
        String expectedPrevious = ChainDefinition.GENESIS_HASH;
        long expectedIndex = 1;
        for (int i = 1; error == null && i < blocks.size(); i++) {
            BlockRaw block = blocks.get(i);
            checked++;
            try {
                if (!ChainDefinition.CHAIN_ID.equals(block.chainId())) {
                    error = "El bloque " + block.index() + " pertenece a una cadena distinta.";
                    break;
                }
                if (block.index() != expectedIndex) {
                    error =
                            "Índice inesperado en el bloque "
                                    + block.index()
                                    + "; se esperaba "
                                    + expectedIndex
                                    + ".";
                    break;
                }
                if (!expectedPrevious.equals(block.previousHash())) {
                    error = "El enlace anterior no coincide en el bloque " + block.index() + ".";
                    break;
                }
                if (block.formatVersion() != ChainDefinition.FORMAT_VERSION) {
                    error = "Versión de formato no soportada en el bloque " + block.index() + ".";
                    break;
                }
                if (!"ATTENDANCE_RECORDED".equals(block.eventType())) {
                    error = "Tipo de evento alterado en el bloque " + block.index() + ".";
                    break;
                }
                if (block.teacherId() == null
                        || block.attendanceId() == null
                        || block.serverAt() == null) {
                    error =
                            "La identidad o fecha del bloque "
                                    + block.index()
                                    + " está incompleta.";
                    break;
                }
                String calculated =
                        json.hashGlobalBlock(
                                block.chainId(),
                                block.teacherId(),
                                block.id(),
                                block.index(),
                                block.eventType(),
                                block.attendanceId(),
                                block.serverAt(),
                                block.previousHash(),
                                block.payload(),
                                block.formatVersion());
                if (!calculated.equals(block.hash())) {
                    error = "El hash no coincide en el bloque " + block.index() + ".";
                    break;
                }
                LinkedRecord record = findLinkedRecord(block.attendanceId());
                if (record == null) {
                    error = "El bloque " + block.index() + " no tiene un registro de asistencia.";
                    break;
                }
                if (!block.teacherId().equals(record.teacherId())) {
                    error =
                            "El registro asociado al bloque "
                                    + block.index()
                                    + " pertenece a otro docente.";
                    break;
                }
                if (!block.serverAt().equals(record.receivedAt())) {
                    error =
                            "La fecha del bloque "
                                    + block.index()
                                    + " no coincide con la recepción del registro.";
                    break;
                }
                if (!block.payload().equals(record.chainPayload())) {
                    error =
                            "El payload del bloque "
                                    + block.index()
                                    + " no coincide con el snapshot del registro.";
                    break;
                }
                String rowMismatch = attendanceMismatch(block.payload(), record);
                if (rowMismatch != null) {
                    error =
                            "Los datos de asistencia asociados al bloque "
                                    + block.index()
                                    + " fueron alterados: "
                                    + rowMismatch
                                    + ".";
                    break;
                }
                String configMismatch = configurationVersionsExist(block.payload());
                if (configMismatch != null) {
                    error =
                            "Falta o fue alterada una versión de geocerca referida por el bloque "
                                    + block.index()
                                    + ".";
                    break;
                }
                expectedPrevious = block.hash();
                expectedIndex++;
            } catch (IllegalArgumentException ex) {
                error =
                        "El payload o la evidencia JSON del bloque "
                                + block.index()
                                + " no puede interpretarse.";
            }
        }
        if (error == null) {
            long attendanceCount =
                    jdbc.queryForObject("select count(*) from attendances", Long.class);
            if (attendanceCount != blocks.size() - 1L)
                error = "La cantidad de asistencias no coincide con los bloques globales.";
        }
        if (error == null) {
            GlobalHead head = readHead();
            BlockRaw last = blocks.getLast();
            if (head == null)
                error = "Falta la cabecera global de la cadena.";
            else if (!ChainDefinition.CHAIN_ID.equals(head.chainId()))
                error = "La cabecera pertenece a una cadena distinta.";
            else if (head.totalBlocks() != blocks.size()
                    || head.lastIndex() != last.index()
                    || !head.lastHash().equals(last.hash()))
                error = "La cabecera global no coincide con el último bloque.";
        }
        return new VerifyView(error == null, checked, error);
    }

    private String genesisError(BlockRaw block) {
        if (!ChainDefinition.GENESIS_BLOCK_ID.equals(block.id()))
            return "El identificador del génesis fue alterado.";
        if (block.index() != 0)
            return "El primer bloque no tiene índice génesis 0.";
        if (!ChainDefinition.CHAIN_ID.equals(block.chainId()))
            return "El génesis pertenece a una cadena distinta.";
        if (!"GENESIS".equals(block.eventType()))
            return "El bloque 0 no es de tipo GENESIS.";
        if (block.teacherId() != null || block.attendanceId() != null || block.serverAt() != null)
            return "El génesis contiene una identidad o fecha inesperada.";
        if (block.formatVersion() != ChainDefinition.FORMAT_VERSION)
            return "La versión del génesis no es compatible.";
        if (!ChainDefinition.GENESIS_PREVIOUS_HASH.equals(block.previousHash()))
            return "El enlace anterior del génesis no son 64 ceros.";
        String expectedPayload =
                json.write(java.util.Map.of("phrase", ChainDefinition.GENESIS_PHRASE));
        if (!expectedPayload.equals(block.payload()))
            return "La frase o el payload del génesis fueron alterados.";
        if (!ChainDefinition.GENESIS_HASH.equals(json.sha256(ChainDefinition.GENESIS_PHRASE))
                || !ChainDefinition.GENESIS_HASH.equals(block.hash()))
            return "El hash SHA-256 de la frase génesis no coincide.";
        return null;
    }

    private List<BlockRaw> readAll() {
        return jdbc.query(
                """
                select id,chain_id,block_index,event_type,teacher_id,attendance_id,server_at,payload,previous_hash,hash,format_version
                from global_chain_blocks order by block_index
                """,
                (rs, row) -> mapBlock(rs));
    }

    private List<BlockRaw> readVisible(UUID teacherId) {
        return jdbc.query(
                """
                select id,chain_id,block_index,event_type,teacher_id,attendance_id,server_at,payload,previous_hash,hash,format_version
                from global_chain_blocks where block_index=0 or teacher_id=? order by block_index
                """,
                (rs, row) -> mapBlock(rs),
                teacherId);
    }

    private BlockRaw mapBlock(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp stamp = rs.getTimestamp("server_at");
        return new BlockRaw(
                rs.getObject("id", UUID.class),
                rs.getString("chain_id"),
                rs.getLong("block_index"),
                rs.getString("event_type"),
                rs.getObject("teacher_id", UUID.class),
                rs.getObject("attendance_id", UUID.class),
                stamp == null ? null : stamp.toInstant(),
                rs.getString("payload"),
                rs.getString("previous_hash"),
                rs.getString("hash"),
                rs.getInt("format_version"));
    }

    private BlockView view(BlockRaw block) {
        JsonNode payload;
        try {
            payload = json.read(block.payload());
        } catch (IllegalArgumentException ex) {
            payload = TextNode.valueOf(block.payload());
        }
        return new BlockView(
                block.id(),
                block.index(),
                block.eventType(),
                block.teacherId(),
                block.attendanceId(),
                block.serverAt(),
                payload,
                block.previousHash(),
                block.hash(),
                block.formatVersion());
    }

    private GlobalHead readHead() {
        List<GlobalHead> rows =
                jdbc.query(
                        "select chain_id,last_index,last_hash,total_blocks from global_chain_head where id=1",
                        (rs, row) ->
                                new GlobalHead(
                                        rs.getString(1),
                                        rs.getLong(2),
                                        rs.getString(3),
                                        rs.getLong(4)));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private LinkedRecord findLinkedRecord(UUID attendanceId) {
        List<LinkedRecord> found =
                jdbc.query(
                        """
                select teacher_id,occurrence_id,received_at,status,spatial_result,temporal_result,reason,justification,location,
                       location_issue,configuration_snapshot,chain_payload
                from attendances where id=?
                """,
                        (rs, row) ->
                                new LinkedRecord(
                                        rs.getObject("teacher_id", UUID.class),
                                        rs.getObject("occurrence_id", UUID.class),
                                        rs.getTimestamp("received_at").toInstant(),
                                        rs.getString("status"),
                                        rs.getString("spatial_result"),
                                        rs.getString("temporal_result"),
                                        rs.getString("reason"),
                                        rs.getString("justification"),
                                        rs.getString("location"),
                                        rs.getString("location_issue"),
                                        rs.getString("configuration_snapshot"),
                                        rs.getString("chain_payload")),
                        attendanceId);
        return found.isEmpty() ? null : found.getFirst();
    }

    private String attendanceMismatch(String payload, LinkedRecord record) {
        JsonNode p = json.read(payload);
        if (!record.occurrenceId().toString().equals(p.path("occurrenceId").asText()))
            return "ocurrencia";
        if (!record.teacherId().toString().equals(p.path("teacherId").asText()))
            return "docente";
        if (!record.status().equals(p.path("status").asText()))
            return "estado";
        if (!record.spatialResult().equals(p.path("spatialResult").asText()))
            return "resultado espacial";
        if (!record.temporalResult().equals(p.path("temporalResult").asText()))
            return "resultado temporal";
        if (!Objects.equals(record.reason(), nullableText(p.get("reason"))))
            return "motivo";
        if (!Objects.equals(record.justification(), nullableText(p.get("justification"))))
            return "justificación";
        if (!Objects.equals(record.locationIssue(), nullableText(p.get("locationIssue"))))
            return "incidencia de ubicación";
        if (!record.receivedAt().toString().equals(p.path("receivedAt").asText()))
            return "fecha de recepción";
        JsonNode rowLocation = record.location() == null ? null : json.read(record.location());
        JsonNode payloadLocation = p.get("location");
        if (!Objects.equals(
                rowLocation == null ? null : json.write(rowLocation),
                payloadLocation == null || payloadLocation.isNull()
                        ? null
                        : json.write(payloadLocation)))
            return "ubicación";
        JsonNode rowConfiguration = json.read(record.configurationSnapshot());
        if (!json.write(rowConfiguration).equals(json.write(p.get("configuration"))))
            return "snapshot de configuración";
        return null;
    }

    private String configurationVersionsExist(String payload) {
        JsonNode config = json.read(payload).path("configuration");
        JsonNode campus = config.path("campusGeofence");
        if (!campus.isMissingNode() && !campus.isNull() && !versionMatches(campus))
            return "campus";
        for (JsonNode module : config.path("allowedModules"))
            if (!versionMatches(module))
                return "módulo";
        return null;
    }

    private boolean versionMatches(JsonNode ref) {
        List<VersionSnapshot> found =
                jdbc.query(
                        "select campus_id,name,type,parent_id,geometry_json,active from geofence_versions where geofence_id=? and version=?",
                        (rs, row) ->
                                new VersionSnapshot(
                                        rs.getObject("campus_id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("type"),
                                        rs.getObject("parent_id", UUID.class),
                                        rs.getString("geometry_json"),
                                        rs.getBoolean("active")),
                        UUID.fromString(ref.path("id").asText()),
                        ref.path("version").asLong());
        if (found.isEmpty())
            return false;
        VersionSnapshot s = found.getFirst();
        UUID parent =
                ref.path("parentId").isNull()
                        ? null
                        : UUID.fromString(ref.path("parentId").asText());
        return s.campusId().equals(UUID.fromString(ref.path("campusId").asText()))
                && s.name().equals(ref.path("name").asText())
                && s.type().equals(ref.path("type").asText())
                && Objects.equals(s.parentId(), parent)
                && s.active() == ref.path("active").asBoolean()
                && json.write(json.read(s.geometryJson())).equals(json.write(ref.path("geometry")));
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private record BlockRaw(
            UUID id,
            String chainId,
            long index,
            String eventType,
            UUID teacherId,
            UUID attendanceId,
            Instant serverAt,
            String payload,
            String previousHash,
            String hash,
            int formatVersion
    ) {
    }

    private record LinkedRecord(
            UUID teacherId,
            UUID occurrenceId,
            Instant receivedAt,
            String status,
            String spatialResult,
            String temporalResult,
            String reason,
            String justification,
            String location,
            String locationIssue,
            String configurationSnapshot,
            String chainPayload
    ) {
    }

    private record VersionSnapshot(
            UUID campusId,
            String name,
            String type,
            UUID parentId,
            String geometryJson,
            boolean active
    ) {
    }

    private record GlobalHead(
            String chainId,
            long lastIndex,
            String lastHash,
            long totalBlocks
    ) {
    }
}
