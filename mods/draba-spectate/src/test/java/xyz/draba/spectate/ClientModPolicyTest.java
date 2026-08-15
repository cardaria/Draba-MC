package xyz.draba.spectate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModPolicyTest {
    private static final ClientModPolicy.PolicyConfig POLICY =
            new ClientModPolicy.PolicyConfig(
                    true,
                    100,
                    List.of("automodpack", "hotbar-keys", "sodium"),
                    List.of("automodpack", "draba_spectate", "hotbar-keys"),
                    "extra", "missing", "update", "timeout", "malformed");

    @Test
    void acceptsOnlyTheOfficialTopLevelSet() {
        ClientModPolicy.Evaluation result = ClientModPolicy.evaluate(POLICY, List.of(
                "minecraft", "java", "fabricloader", "fabric-api",
                "fabric-rendering-v1", "draba_spectate", "automodpack",
                "hotbar-keys", "sodium"));

        assertTrue(result.valid());
        assertEquals(9, result.reportedCount());
    }

    @Test
    void rejectsAnExtraClientMod() {
        ClientModPolicy.Evaluation result = ClientModPolicy.evaluate(POLICY, List.of(
                "minecraft", "java", "fabricloader", "fabric-api",
                "draba_spectate", "automodpack", "hotbar-keys", "freecam"));

        assertFalse(result.valid());
        assertEquals(List.of("freecam"), result.disallowed());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void rejectsMissingRequiredPackComponents() {
        ClientModPolicy.Evaluation result = ClientModPolicy.evaluate(POLICY, List.of(
                "minecraft", "java", "fabricloader", "fabric-api",
                "draba_spectate", "automodpack"));

        assertFalse(result.valid());
        assertEquals(List.of("hotbar-keys"), result.missing());
    }

    @Test
    void normalizesCaseAndRejectsMalformedIds() {
        ClientModPolicy.Evaluation normalized = ClientModPolicy.evaluate(POLICY, List.of(
                "MINECRAFT", "JAVA", "FABRICLOADER", "FABRIC-API",
                "DRABA_SPECTATE", "AUTOMODPACK", "HOTBAR-KEYS"));
        ClientModPolicy.Evaluation malformed = ClientModPolicy.evaluate(POLICY, List.of(
                "minecraft", "java", "fabricloader", "fabric-api",
                "draba_spectate", "automodpack", "hotbar-keys", "bad.mod"));

        assertTrue(normalized.valid());
        assertEquals(List.of("bad.mod"), malformed.disallowed());
    }
}
