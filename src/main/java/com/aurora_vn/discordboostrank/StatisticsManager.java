package com.aurora_vn.discordboostrank;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatisticsManager {

    private final DiscordBoostRank plugin;
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();

    public StatisticsManager(DiscordBoostRank plugin) {
        this.plugin = plugin;
        loadFromDisk();
    }

    public void recordBoostChange(UUID uuid, String rank, int boostCount) {
        if (!plugin.getConfig().getBoolean("statistics.enabled", true)) {
            return;
        }
        playerStats.put(uuid, new PlayerStats(rank, boostCount));
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("statistics.enabled", true)) {
            return;
        }

        File statsFile = new File(config.getString("statistics.file-path", "plugins/DiscordBoostRank/stats.yml"));
        if (statsFile.getParentFile() != null && !statsFile.getParentFile().exists()) {
            statsFile.getParentFile().mkdirs();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("totals.total-boosters", playerStats.size());
        yaml.set("totals.total-boosts-received", playerStats.values().stream()
                .mapToInt(PlayerStats::boosts)
                .sum());

        ConfigurationSection players = yaml.createSection("players");
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            ConfigurationSection section = players.createSection(entry.getKey().toString());
            section.set("rank", entry.getValue().rank());
            section.set("boosts", entry.getValue().boosts());
        }

        try {
            yaml.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save statistics: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        FileConfiguration config = plugin.getConfig();
        File statsFile = new File(config.getString("statistics.file-path", "plugins/DiscordBoostRank/stats.yml"));
        if (!statsFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(statsFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = players.getConfigurationSection(key);
                if (section != null) {
                    String rank = section.getString("rank", "");
                    int boosts = section.getInt("boosts", 0);
                    playerStats.put(uuid, new PlayerStats(rank, boosts));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private record PlayerStats(String rank, int boosts) {}
}
