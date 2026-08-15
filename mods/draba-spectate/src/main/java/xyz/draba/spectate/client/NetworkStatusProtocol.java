package xyz.draba.spectate.client;

import java.util.UUID;

/**
 * Decodes the transient join time carried by Draba's Velocity status sample.
 * The UUID belongs only to the server-list sample; it never replaces a
 * player's authenticated UUID in gameplay.
 */
final class NetworkStatusProtocol {
    static final long JOIN_TIME_MAGIC = 0x44524142414A4F49L; // "DRABAJOI"
    private static final long MAX_FUTURE_SKEW_SECONDS = 300L;

    private NetworkStatusProtocol() {
    }

    static long decodeJoinEpochSeconds(UUID sampleId, long nowEpochSeconds) {
        if (sampleId == null || sampleId.getMostSignificantBits() != JOIN_TIME_MAGIC) {
            return -1L;
        }
        long joinedAt = sampleId.getLeastSignificantBits();
        if (joinedAt <= 0L || joinedAt > nowEpochSeconds + MAX_FUTURE_SKEW_SECONDS) {
            return -1L;
        }
        return joinedAt;
    }
}
