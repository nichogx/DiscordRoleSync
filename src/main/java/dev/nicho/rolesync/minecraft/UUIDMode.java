package dev.nicho.rolesync.minecraft;

import java.util.Locale;

public enum UUIDMode {
    DEFAULT,
    ONLINE,
    OFFLINE,
    FALLBACK,
    MANUAL;

    public static UUIDMode fromCaseInsensitive(String mode) {
        return UUIDMode.valueOf(mode.toUpperCase(Locale.ENGLISH));
    }
}
