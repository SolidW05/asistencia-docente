package mx.udg.cut.asistencia.models.chain;

import java.util.UUID;

/** La cadena comienza con un único Génesis, antes de registrar cualquier asistencia. */
public final class ChainDefinition {
    public static final String CHAIN_ID = "GLOBAL_ATTENDANCE";
    public static final UUID GENESIS_BLOCK_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String GENESIS_PHRASE = "And they say chivalry is dead";
    public static final String GENESIS_HASH =
            "47b7663559093619c363151c5e8395df979fb7595b9cde94d1f70f22d5356945";
    public static final String GENESIS_PREVIOUS_HASH = "0".repeat(64);
    public static final int FORMAT_VERSION = 2;

    private ChainDefinition() {
    }
}
