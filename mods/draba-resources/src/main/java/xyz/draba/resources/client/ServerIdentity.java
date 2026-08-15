package xyz.draba.resources.client;

import java.util.Locale;

public final class ServerIdentity {
    public enum Icon {
        SURVIVAL,
        HARDCORE
    }

    private ServerIdentity() {
    }

    public static Icon iconForAddress(String address) {
        if (address != null
                && address.toLowerCase(Locale.ROOT).contains("hardcore.example.com")) {
            return Icon.HARDCORE;
        }
        return Icon.SURVIVAL;
    }
}
