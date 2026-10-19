package mx.udg.cut.asistencia.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Serialización canónica: claves ordenadas, JSON UTF-8 sin espacios y números normalizados. */
@Component
public class CanonicalJson {
    private final ObjectMapper mapper;

    public CanonicalJson(ObjectMapper mapper) {
        this.mapper =
                mapper.copy()
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                        .configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
                        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
                        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(normalize(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el payload", e);
        }
    }

    private Object normalize(Object value) {
        if (value instanceof JsonNode node) {
            if (node.isObject()) {
                Map<String, Object> sorted = new TreeMap<>();
                node.fields()
                        .forEachRemaining(e -> sorted.put(e.getKey(), normalize(e.getValue())));
                return sorted;
            }
            if (node.isArray()) {
                List<Object> values = new ArrayList<>();
                node.forEach(v -> values.add(normalize(v)));
                return values;
            }
            if (node.isNumber())
                return decimal(node.decimalValue());
            if (node.isTextual())
                return node.textValue();
            if (node.isBoolean())
                return node.booleanValue();
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), normalize(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalize).toList();
        }
        if (value instanceof BigDecimal number)
            return decimal(number);
        if (value instanceof Number number)
            return decimal(new BigDecimal(number.toString()));
        return value;
    }

    private BigDecimal decimal(BigDecimal value) {
        BigDecimal n = value.stripTrailingZeros();
        return n.signum() == 0 ? BigDecimal.ZERO : n;
    }

    public JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON inválido", e);
        }
    }

    /** Formato v2 de la única cadena global. */
    public String hashGlobalBlock(
            String chainId,
            UUID teacherId,
            UUID blockId,
            long index,
            String eventType,
            UUID attendanceId,
            Instant serverAt,
            String previousHash,
            String payload,
            int formatVersion) {
        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("attendanceId", attendanceId);
        envelope.put("blockId", blockId);
        envelope.put("chainId", chainId);
        envelope.put("eventType", eventType);
        envelope.put("formatVersion", formatVersion);
        envelope.put("index", index);
        envelope.put("payload", read(payload));
        envelope.put("previousHash", previousHash);
        envelope.put("serverAt", serverAt);
        envelope.put("teacherId", teacherId);
        return sha256(write(envelope));
    }

    public String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
