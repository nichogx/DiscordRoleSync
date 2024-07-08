package dev.nicho.rolesync.integrations.placeholders;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.DatabaseHandler;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RoleSyncPlaceholderExpansion extends PlaceholderExpansion {

    private final RoleSync plugin;

    private final LoadingCache<PlaceholderCacheKey, Optional<String>> cache;

    public RoleSyncPlaceholderExpansion(RoleSync plugin) {
        this.plugin = plugin;

        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .build(new CacheLoader<PlaceholderCacheKey, Optional<String>>() {
                    @Override
                    public @NotNull Optional<String> load(@NotNull PlaceholderCacheKey key) {
                        return Optional.ofNullable(uncachedRequest(key.player, key.placeholder));
                    }
                });
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authorList = plugin.getDescription().getAuthors();
        if (authorList.isEmpty()) {
            return "";
        }

        return authorList.getFirst();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "drs";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        try {
            OfflinePlayer playerKey = isPlayerAgnostic(params) ? null : player;
            return cache.get(new PlaceholderCacheKey(playerKey, params)).orElse(null);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isPlayerAgnostic(String placeholder) {
        return placeholder.equals("linked_users");
    }

    public @Nullable String uncachedRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        plugin.debugLog(String.format("Received uncached PlaceholderAPI request. " +
                "main_thread=%s params=%s player=%s", Bukkit.isPrimaryThread(), params, player));

        if (player == null && !isPlayerAgnostic(params)) {
            return null;
        }

        try {
            switch (params.toLowerCase(Locale.ENGLISH)) {
                case "linked_users": {
                    return placeholderLinkedUsers();
                }

                case "link_status": {
                    assert player != null; // Should be handled by `isPlayerAgnostic`
                    return placeholderPlayerLinkStatus(player);
                }

                case "discord_username": {
                    assert player != null; // Should be handled by `isPlayerAgnostic`
                    return placeholderPlayerDiscordUsername(player);
                }

                case "discord_nick": {
                    assert player != null; // Should be handled by `isPlayerAgnostic`
                    return placeholderPlayerDiscordNick(player);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning(String.format("Error while computing placeholder %s for player %s", params, player));
        }

        return null;
    }

    private String placeholderLinkedUsers() throws SQLException {
        return String.valueOf(plugin.getDb().getLinkedUserCount());
    }

    private String placeholderPlayerLinkStatus(@NotNull OfflinePlayer player) throws SQLException {
        boolean linked = plugin.getDb().getLinkedUserInfo(player.getUniqueId().toString()) != null;
        return linked ?
                plugin.getLanguage().getString("linkStatus.linked") :
                plugin.getLanguage().getString("linkStatus.notLinked");
    }

    private String placeholderPlayerDiscordUsername(@NotNull OfflinePlayer player) throws SQLException {
        DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(player.getUniqueId().toString());
        if (userInfo == null) {
            return "";
        }

        String username = plugin.getBot().getDiscordUsername(userInfo.discordId);
        if (username == null) {
            return "";
        }

        return username;
    }

    private String placeholderPlayerDiscordNick(@NotNull OfflinePlayer player) throws SQLException {
        DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(player.getUniqueId().toString());
        if (userInfo == null) {
            return "";
        }

        String nick = plugin.getBot().getDiscordNickname(userInfo.discordId);
        if (nick == null) {
            return "";
        }

        return nick;
    }
}
