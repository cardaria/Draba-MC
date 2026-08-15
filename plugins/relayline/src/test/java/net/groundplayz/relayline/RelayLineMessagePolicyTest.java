package net.groundplayz.relayline;

import java.util.Set;

public final class RelayLineMessagePolicyTest {
    public static void main(String[] args) {
        Set<String> prefixes = Set.of("xaero-waypoint:");

        require(RelayLineMessagePolicy.isLocalOnly(
                "xaero-waypoint:Home:H:1:64:2:0:false:0:Internal-overworld", prefixes));
        require(RelayLineMessagePolicy.isLocalOnly(
                "  XAERO-WAYPOINT:Nether:N:3:70:4:0:false:0:Internal-the-nether", prefixes));
        require(!RelayLineMessagePolicy.isLocalOnly("Meet at the waypoint", prefixes));
        require(!RelayLineMessagePolicy.isLocalOnly("", prefixes));
        require(!RelayLineMessagePolicy.isLocalOnly(null, prefixes));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("RelayLine local-only message policy failed");
        }
    }
}
