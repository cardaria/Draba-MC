package xyz.draba.spectate.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateActionPayloadTest {
    @Test
    void acceptsOnlyDefinedActions() {
        assertTrue(new SpectateActionPayload(SpectateActionPayload.START).isValid());
        assertTrue(new SpectateActionPayload(SpectateActionPayload.PREVIOUS).isValid());
        assertTrue(new SpectateActionPayload(SpectateActionPayload.NEXT).isValid());
        assertTrue(new SpectateActionPayload(SpectateActionPayload.STOP).isValid());
        assertFalse(new SpectateActionPayload(3).isValid());
        assertFalse(new SpectateActionPayload(Integer.MIN_VALUE).isValid());
    }
}
