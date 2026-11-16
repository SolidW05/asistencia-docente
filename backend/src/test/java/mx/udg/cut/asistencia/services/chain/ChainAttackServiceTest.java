package mx.udg.cut.asistencia.services.chain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import mx.udg.cut.asistencia.exceptions.ApiException;
import mx.udg.cut.asistencia.utils.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

class ChainAttackServiceTest {
    @Test
    void attackRequiresBothDemoProfileAndExplicitFlag() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChainService chain = mock(ChainService.class);
        var json = new CanonicalJson(new ObjectMapper());
        var demo = new MockEnvironment();
        demo.setActiveProfiles("demo");
        for (var service : new ChainAttackService[] {
                new ChainAttackService(jdbc, json, chain, demo, false),
                new ChainAttackService(jdbc, json, chain, new MockEnvironment(), true)}) {
            assertFalse(service.status().enabled());
            assertThrows(ApiException.class, () -> service.attack(UUID.randomUUID()));
            assertThrows(ApiException.class, service::restore);
        }
        verifyNoInteractions(jdbc, chain);
    }

    @Test
    void canonicalJsonNormalizesKeysNumbersAndKnownSha256() {
        var json = new CanonicalJson(new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        assertEquals(json.write(json.read("{\"b\":1.00,\"a\":[0.0,2]}")),
                json.write(json.read("{\"a\":[0,2.0],\"b\":1}")));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", json.sha256("abc"));
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        String hash = json.hashGlobalBlock("GLOBAL_ATTENDANCE", id, id, 1, "ATTENDANCE_RECORDED", id, time, "0".repeat(64), "{\"status\":\"CONFIRMED\"}", 2);
        assertEquals("f7ad4624ab829b6e4a5976602d0238a8ce764d8e44b0cf64870dd4d60a8febd7", hash);
        assertNotEquals(hash, json.hashGlobalBlock("GLOBAL_ATTENDANCE", id, UUID.randomUUID(), 1, "ATTENDANCE_RECORDED", id, time, "0".repeat(64), "{\"status\":\"CONFIRMED\"}", 2));
        assertNotEquals(hash, json.hashGlobalBlock("GLOBAL_ATTENDANCE", id, id, 1, "ATTENDANCE_RECORDED", id, time.plusSeconds(1), "0".repeat(64), "{\"status\":\"CONFIRMED\"}", 2));
        assertNotEquals(hash, json.hashGlobalBlock("GLOBAL_ATTENDANCE", id, id, 1, "ATTENDANCE_RECORDED", id, time, "0".repeat(64), "{\"status\":\"PENDING_REVIEW\"}", 2));
        assertNotEquals(hash, json.hashGlobalBlock("GLOBAL_ATTENDANCE", id, id, 2, "ATTENDANCE_RECORDED", id, time, "0".repeat(64), "{\"status\":\"CONFIRMED\"}", 2));
        assertNotEquals(hash, json.hashGlobalBlock("GLOBAL_ATTENDANCE", id, id, 1, "ATTENDANCE_RECORDED", id, time, "1".repeat(64), "{\"status\":\"CONFIRMED\"}", 2));
    }
}
