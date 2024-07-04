package dev.nicho.rolesync.integrations.placeholders;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class PlaceholderCacheKey {

    final OfflinePlayer player;
    final String placeholder;

    PlaceholderCacheKey(@Nullable OfflinePlayer player, String placeholder) {
        this.player = player;
        this.placeholder = placeholder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlaceholderCacheKey that = (PlaceholderCacheKey) o;
        return placeholder.equals(that.placeholder) &&
                String.valueOf(player).equals(String.valueOf(that.player));
    }

    @Override
    public int hashCode() {
        return Objects.hash(String.valueOf(player), placeholder);
    }
}
