package mx.udg.cut.asistencia.services.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.udg.cut.asistencia.dto.chain.VerifyView;
import mx.udg.cut.asistencia.models.chain.ChainDefinition;
import mx.udg.cut.asistencia.utils.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ChainServiceVerificationTest {
    private static final UUID OWNER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REVIEWER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ATTENDANCE_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OCCURRENCE_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-26T09:59:30Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-10-26T10:00:00Z");

    private final CanonicalJson json =
            new CanonicalJson(
                    new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    @Test
    void verifiesInitialAndConfirmedHistoryAndRejectsTamperedEvent() {
        MemoryChainJdbc jdbc = new MemoryChainJdbc();
        ChainService chain = new ChainService(jdbc, json);
        Map<String, Object> attendancePayload = attendancePayload();
        String initialPayload = json.write(attendancePayload);

        jdbc.attendances.put(ATTENDANCE_ID, attendanceRow("PENDING_REVIEW", initialPayload));
        jdbc.blocks.add(genesisBlock());
        Map<String, Object> recorded = block(
                1,
                "ATTENDANCE_RECORDED",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                RECEIVED_AT,
                initialPayload,
                ChainDefinition.GENESIS_HASH);
        jdbc.blocks.add(recorded);
        jdbc.updateHead();

        VerifyView before = chain.verify(OWNER_ID);

        assertTrue(before.valid(), before.firstError());
        assertEquals(2, before.checkedBlocks());

        Map<String, Object> reviewPayload = new LinkedHashMap<>();
        reviewPayload.put("actorId", REVIEWER_ID.toString());
        reviewPayload.put("actorRole", "JEFE");
        reviewPayload.put("previousStatus", "PENDING_REVIEW");
        reviewPayload.put("status", "CONFIRMED");
        reviewPayload.put("reason", "Validación manual");
        reviewPayload.put("reviewerName", "Jefe de prueba");
        reviewPayload.put("attendanceId", ATTENDANCE_ID.toString());
        reviewPayload.put("teacherId", OWNER_ID.toString());
        reviewPayload.put("occurrenceId", OCCURRENCE_ID.toString());
        reviewPayload.put("changedAt", REVIEWED_AT.toString());
        Map<String, Object> review = block(
                2,
                "ATTENDANCE_STATUS_CHANGED",
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                REVIEWED_AT,
                json.write(reviewPayload),
                (String) recorded.get("hash"));
        jdbc.blocks.add(review);
        jdbc.attendances.put(ATTENDANCE_ID, attendanceRow("CONFIRMED", initialPayload));
        jdbc.updateHead();

        VerifyView after = chain.verify(OWNER_ID);

        assertTrue(after.valid(), after.firstError());
        assertEquals(3, after.checkedBlocks());

        review.put("payload", json.write(Map.of("status", "CONFIRMED", "reason", "editado")));
        VerifyView tampered = chain.verify(OWNER_ID);

        assertFalse(tampered.valid());
        assertEquals(3, tampered.checkedBlocks());
        assertTrue(tampered.firstError().contains("hash"));
    }

    private Map<String, Object> attendancePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teacherId", OWNER_ID);
        payload.put("occurrenceId", OCCURRENCE_ID);
        payload.put("receivedAt", RECEIVED_AT.toString());
        payload.put("status", "PENDING_REVIEW");
        payload.put("spatialResult", "NO_LOCATION");
        payload.put("temporalResult", "IN_WINDOW");
        payload.put("reason", "Permiso de ubicación denegado");
        payload.put("justification", "El sensor no respondió");
        payload.put("location", null);
        payload.put("locationIssue", null);
        payload.put("configuration", Map.of());
        return payload;
    }

    private Map<String, Object> attendanceRow(String status, String chainPayload) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("teacher_id", OWNER_ID);
        row.put("occurrence_id", OCCURRENCE_ID);
        row.put("received_at", Timestamp.from(RECEIVED_AT));
        row.put("status", status);
        row.put("spatial_result", "NO_LOCATION");
        row.put("temporal_result", "IN_WINDOW");
        row.put("reason", "Permiso de ubicación denegado");
        row.put("justification", "El sensor no respondió");
        row.put("location", null);
        row.put("location_issue", null);
        row.put("configuration_snapshot", "{}");
        row.put("chain_payload", chainPayload);
        return row;
    }

    private Map<String, Object> genesisBlock() {
        Map<String, Object> genesis = new LinkedHashMap<>();
        genesis.put("id", ChainDefinition.GENESIS_BLOCK_ID);
        genesis.put("chain_id", ChainDefinition.CHAIN_ID);
        genesis.put("block_index", 0L);
        genesis.put("event_type", "GENESIS");
        genesis.put("teacher_id", null);
        genesis.put("attendance_id", null);
        genesis.put("server_at", null);
        genesis.put("payload", json.write(Map.of("phrase", ChainDefinition.GENESIS_PHRASE)));
        genesis.put("previous_hash", ChainDefinition.GENESIS_PREVIOUS_HASH);
        genesis.put("hash", ChainDefinition.GENESIS_HASH);
        genesis.put("format_version", ChainDefinition.FORMAT_VERSION);
        return genesis;
    }

    private Map<String, Object> block(
            long index,
            String eventType,
            UUID id,
            Instant serverAt,
            String payload,
            String previousHash) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", id);
        block.put("chain_id", ChainDefinition.CHAIN_ID);
        block.put("block_index", index);
        block.put("event_type", eventType);
        block.put("teacher_id", OWNER_ID);
        block.put("attendance_id", ATTENDANCE_ID);
        block.put("server_at", Timestamp.from(serverAt));
        block.put("payload", payload);
        block.put("previous_hash", previousHash);
        block.put(
                "hash",
                json.hashGlobalBlock(
                        ChainDefinition.CHAIN_ID,
                        OWNER_ID,
                        id,
                        index,
                        eventType,
                        ATTENDANCE_ID,
                        serverAt,
                        previousHash,
                        payload,
                        ChainDefinition.FORMAT_VERSION));
        block.put("format_version", ChainDefinition.FORMAT_VERSION);
        return block;
    }

    private final class MemoryChainJdbc extends JdbcTemplate {
        private final List<Map<String, Object>> blocks = new ArrayList<>();
        private final Map<UUID, Map<String, Object>> attendances = new LinkedHashMap<>();
        private Map<String, Object> head;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            List<Map<String, Object>> rows;
            if (sql.contains("from global_chain_blocks")) {
                rows = blocks.stream()
                        .sorted(Comparator.comparingLong(row -> ((Number) row.get("block_index")).longValue()))
                        .toList();
            } else if (sql.contains("from global_chain_head")) {
                rows = head == null ? List.of() : List.of(head);
            } else {
                throw new AssertionError("Unexpected query: " + sql);
            }
            return mapRows(rows, rowMapper);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from attendances where id=?")) {
                Map<String, Object> row = attendances.get((UUID) args[0]);
                return mapRows(row == null ? List.of() : List.of(row), rowMapper);
            }
            if (sql.contains("from geofence_versions")) return List.of();
            if (sql.contains("from class_occurrences o")) {
                Map<String, Object> row = Map.of("created_by", REVIEWER_ID);
                return mapRows(List.of(row), rowMapper);
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            if (!sql.contains("count(*) from attendances"))
                throw new AssertionError("Unexpected scalar query: " + sql);
            return requiredType.cast((long) attendances.size());
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (!sql.contains("count(*) from teachers where id=?"))
                throw new AssertionError("Unexpected parameterized scalar query: " + sql);
            long count = REVIEWER_ID.equals(args[0]) ? 1L : 0L;
            return requiredType.cast(count);
        }

        private void updateHead() {
            Map<String, Object> last = blocks.get(blocks.size() - 1);
            head = new LinkedHashMap<>();
            head.put("chain_id", ChainDefinition.CHAIN_ID);
            head.put("last_index", last.get("block_index"));
            head.put("last_hash", last.get("hash"));
            head.put("total_blocks", (long) blocks.size());
        }

        private <T> List<T> mapRows(List<Map<String, Object>> rows, RowMapper<T> mapper) {
            List<T> result = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                try {
                    result.add(mapper.mapRow(resultSet(rows.get(i)), i));
                } catch (SQLException ex) {
                    throw new AssertionError("Unexpected SQL mapping failure", ex);
                }
            }
            return result;
        }

        private ResultSet resultSet(Map<String, Object> values) {
            List<Object> ordered = new ArrayList<>(values.values());
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if (args != null && args.length > 0 && name.startsWith("get")) {
                                    Object key = args[0];
                                    Object value =
                                            key instanceof Number number
                                                    ? ordered.get(number.intValue() - 1)
                                                    : values.get(key.toString());
                                    return switch (name) {
                                        case "getString" -> value == null ? null : value.toString();
                                        case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                                        case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                                        case "getBoolean" -> value != null && (Boolean) value;
                                        case "getTimestamp" ->
                                                value instanceof Instant instant
                                                        ? Timestamp.from(instant)
                                                        : (Timestamp) value;
                                        case "getObject" -> value;
                                        case "wasNull" -> value == null;
                                        default -> defaultValue(method.getReturnType());
                                    };
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            if (type == char.class) return '\0';
            return null;
        }
    }
}


