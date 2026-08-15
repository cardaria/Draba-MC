package xyz.draba.spectate.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectateStatePayloadTest {
    @Test
    void roundTripPreservesSelectedTargetEntity() {
        SpectateStatePayload expected = new SpectateStatePayload(
                true, false, 0, false, "", "Target", 481, 2, 5);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);

        SpectateStatePayload.CODEC.encode(buffer, expected);

        assertEquals(expected, SpectateStatePayload.CODEC.decode(buffer));
    }

    @Test
    void roundTripPreservesNoTargetSentinel() {
        SpectateStatePayload expected = new SpectateStatePayload(
                false, false, 0, true, "", "", -1, -1, 0);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);

        SpectateStatePayload.CODEC.encode(buffer, expected);

        assertEquals(expected, SpectateStatePayload.CODEC.decode(buffer));
    }

    @Test
    void roundTripPreservesArmingCountdown() {
        SpectateStatePayload expected = new SpectateStatePayload(
                false, true, 73, false, "", "", -1, -1, 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);

        SpectateStatePayload.CODEC.encode(buffer, expected);

        assertEquals(expected, SpectateStatePayload.CODEC.decode(buffer));
    }
}
