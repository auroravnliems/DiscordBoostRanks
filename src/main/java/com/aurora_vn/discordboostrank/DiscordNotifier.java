package com.aurora_vn.discordboostrank;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.util.UUID;

public class DiscordNotifier {

    private final DiscordBoostRank plugin;

    public DiscordNotifier(DiscordBoostRank plugin) {
        this.plugin = plugin;
    }

    public void sendRankGranted(UUID uuid, String rank, int boosts) {
        sendEmbed("notifications.discord.messages.rank-granted.title",
                "notifications.discord.messages.rank-granted.description",
                "notifications.discord.messages.rank-granted.color",
                uuid, rank, boosts);
    }

    public void sendRankRemoved(UUID uuid, String rank) {
        sendEmbed("notifications.discord.messages.rank-removed.title",
                "notifications.discord.messages.rank-removed.description",
                "notifications.discord.messages.rank-removed.color",
                uuid, rank, 0);
    }

    private void sendEmbed(String titlePath, String descPath, String colorPath,
                           UUID uuid, String rank, int boosts) {
        if (!plugin.getConfig().getBoolean("notifications.discord.enabled", false)) {
            return;
        }

        String channelId = plugin.getConfig().getString("notifications.discord.channel-id", "");
        if (channelId == null || channelId.isBlank()) {
            return;
        }

        JDA jda = DiscordSRV.getPlugin().getJda();
        if (jda == null) {
            plugin.getLogger().warning("JDA not ready; skipping Discord notification.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().warning("Discord channel not found: " + channelId);
            return;
        }

        String playerName = Bukkit.getOfflinePlayer(uuid).getName();
        String title = plugin.getConfig().getString(titlePath, "Boost update");
        String description = plugin.getConfig().getString(descPath, "{player} -> {rank}");
        String colorHex = plugin.getConfig().getString(colorPath, "5865F2");

        description = description
                .replace("{player}", playerName != null ? playerName : uuid.toString())
                .replace("{rank}", rank != null ? rank : "N/A")
                .replace("{boosts}", String.valueOf(boosts));

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description);

        try {
            builder.setColor(Color.decode("#" + colorHex));
        } catch (Exception ignored) {
            builder.setColor(Color.decode("#5865F2"));
        }

        channel.sendMessageEmbeds(builder.build()).queue();
    }
}
