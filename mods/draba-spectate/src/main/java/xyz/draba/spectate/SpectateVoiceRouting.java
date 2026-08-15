package xyz.draba.spectate;

import java.util.Objects;
import java.util.UUID;

final class SpectateVoiceRouting {
    static final String PROXIMITY_SOURCE = "proximity";
    static final String SPECTATOR_SOURCE = "spectator";

    private SpectateVoiceRouting() {
    }

    static boolean replacesAutomaticDelivery(
            String source, boolean senderWatching, boolean receiverWatching) {
        if (!senderWatching && !receiverWatching) {
            return false;
        }
        return PROXIMITY_SOURCE.equals(source) || SPECTATOR_SOURCE.equals(source);
    }

    static boolean needsManualDelivery(boolean senderWatching, boolean receiverWatching) {
        return senderWatching || receiverWatching;
    }

    static boolean canReceiveProximity(
            boolean connected,
            boolean disabled,
            boolean installed,
            UUID senderGroupId,
            UUID receiverGroupId,
            boolean receiverGroupIsolated) {
        return connected
                && !disabled
                && installed
                && !receiverGroupIsolated
                && (senderGroupId == null || !senderGroupId.equals(receiverGroupId));
    }

    static boolean isAudible(VoicePosition source, VoicePosition listener, double distance) {
        if (source == null || listener == null
                || !Double.isFinite(distance) || distance <= 0.0D
                || !source.dimension().equals(listener.dimension())) {
            return false;
        }
        double x = source.x() - listener.x();
        double y = source.y() - listener.y();
        double z = source.z() - listener.z();
        double distanceSquared = x * x + y * y + z * z;
        double limitSquared = distance * distance;
        return Double.isFinite(distanceSquared)
                && Double.isFinite(limitSquared)
                && distanceSquared <= limitSquared;
    }

    static VoicePosition rebase(
            VoicePosition source, VoicePosition listener, VoicePosition camera) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(camera, "camera");
        if (!source.dimension().equals(listener.dimension())) {
            throw new IllegalArgumentException("Source and listener must share a dimension");
        }
        return new VoicePosition(
                camera.dimension(),
                camera.x() + source.x() - listener.x(),
                camera.y() + source.y() - listener.y(),
                camera.z() + source.z() - listener.z());
    }

    record VoicePosition(String dimension, double x, double y, double z) {
        VoicePosition {
            if (dimension == null || dimension.isBlank()
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid voice position");
            }
        }
    }
}
