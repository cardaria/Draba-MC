package xyz.draba.spectate.client;

final class SpectateCountdown {
    private SpectateCountdown() {
    }

    static int displaySeconds(int ticksRemaining) {
        return Math.max(0, (ticksRemaining + 19) / 20);
    }
}
