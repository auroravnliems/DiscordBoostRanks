package com.aurora_vn.discordboostrank;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private final DiscordBoostRank plugin;

    public BackupManager(DiscordBoostRank plugin) {
        this.plugin = plugin;
    }

    public void createBackup() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("backup.enabled", true)) {
            return;
        }

        String backupPath = config.getString("backup.backup-path", plugin.getDataFolder().getPath() + "/backups/");
        File backupDir = new File(backupPath);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            plugin.getLogger().warning("Could not create backup directory: " + backupDir.getAbsolutePath());
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupFile = new File(backupDir, "backup-" + timestamp + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            zipIfExists(new File(plugin.getDataFolder(), "config.yml"), "config.yml", zos);

            String statsPath = config.getString("statistics.file-path", "plugins/DiscordBoostRank/stats.yml");
            zipIfExists(new File(statsPath), "stats.yml", zos);
        } catch (IOException e) {
            plugin.getLogger().warning("Backup failed: " + e.getMessage());
            return;
        }

        pruneOldBackups(backupDir, config.getInt("backup.max-backups", 7));
        plugin.getLogger().info("Created backup: " + backupFile.getName());
    }

    private void pruneOldBackups(File backupDir, int maxBackups) {
        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (backups == null || backups.length <= maxBackups) {
            return;
        }

        List<File> sorted = Arrays.asList(backups);
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (int i = maxBackups; i < sorted.size(); i++) {
            try {
                Files.deleteIfExists(sorted.get(i).toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Could not delete old backup " + sorted.get(i).getName() + ": " + e.getMessage());
            }
        }
    }

    private void zipIfExists(File source, String entryName, ZipOutputStream zos) throws IOException {
        if (!source.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(source)) {
            zos.putNextEntry(new ZipEntry(entryName));
            fis.transferTo(zos);
            zos.closeEntry();
        }
    }
}
