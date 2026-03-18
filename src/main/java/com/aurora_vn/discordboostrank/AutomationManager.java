package com.aurora_vn.discordboostrank;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class AutomationManager {
    
    private final DiscordBoostRank plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    
    public AutomationManager(DiscordBoostRank plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (plugin.getConfig().getBoolean("auto-check.periodic-sync.enabled", true)) {
            int interval = plugin.getConfig().getInt("auto-check.periodic-sync.interval-minutes", 15);
            long ticks = interval * 60L * 20L;
            
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                plugin.getLogger().info("[Auto-Sync] Checking online players...");
                Bukkit.getOnlinePlayers().forEach(player -> {
                    plugin.getBoostChecker().checkPlayer(player.getUniqueId(), false);
                });
            }, ticks, ticks);
            
            tasks.add(task);
            plugin.getLogger().info("Started auto-sync: every " + interval + " minutes (online players)");
        }
        
        if (plugin.getConfig().getBoolean("auto-check.full-sync.enabled", true)) {
            int interval = plugin.getConfig().getInt("auto-check.full-sync.interval-minutes", 60);
            long ticks = interval * 60L * 20L;
            
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                plugin.getLogger().info("[Full-Sync] Checking all linked accounts...");
                plugin.getBoostChecker().checkAllLinkedAccounts();
            }, ticks, ticks);
            
            tasks.add(task);
            plugin.getLogger().info("Started full-sync: every " + interval + " minutes (all players)");
        }
        
        if (plugin.getConfig().getBoolean("backup.enabled", true)) {
            int interval = plugin.getConfig().getInt("backup.interval-hours", 24);
            long ticks = interval * 60L * 60L * 20L;
            
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                plugin.getLogger().info("[Auto-Backup] Creating backup...");
                plugin.getBackupManager().createBackup();
            }, ticks, ticks);
            
            tasks.add(task);
            plugin.getLogger().info("Started auto-backup: every " + interval + " hours");
        }

        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            long ticks = 5 * 60L * 20L;
            
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                plugin.getStatisticsManager().save();
            }, ticks, ticks);
            
            tasks.add(task);
        }
    }
    
    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        plugin.getLogger().info("Stopped all automation tasks");
    }
}
