package com.aurora_vn.discordboostrank;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BoostListener extends ListenerAdapter {
    
    private final DiscordBoostRank plugin;
    private final LuckPerms luckPerms;
    
    public BoostListener(DiscordBoostRank plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }
    
    @Override
    public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent event) {
        Member member = event.getMember();
        String discordId = member.getId();
        
        UUID minecraftUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordId);
        
        if (minecraftUuid == null) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("Discord user " + member.getEffectiveName() + " (" + discordId + ") boosted but has no linked account");
            }
            return;
        }
        
        boolean wasBoosting = event.getOldTimeBoosted() != null;
        boolean isBoosting = event.getNewTimeBoosted() != null;
        
        if (plugin.isDebug()) {
            plugin.getLogger().info("Boost status change for " + member.getEffectiveName() + 
                    " (UUID: " + minecraftUuid + "): " + wasBoosting + " -> " + isBoosting);
        }
        
        if (!wasBoosting && isBoosting) {
            grantBoostRank(minecraftUuid, member.getEffectiveName());
        } else if (wasBoosting && !isBoosting && plugin.shouldRemoveOnUnboost()) {
            removeBoostRank(minecraftUuid, member.getEffectiveName());
        }
    }
    
    private void grantBoostRank(UUID uuid, String discordName) {
        String rankName = plugin.getBoostRank();
        
        CompletableFuture.runAsync(() -> {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            
            if (user == null) {
                plugin.getLogger().warning("Could not load LuckPerms user for UUID: " + uuid);
                return;
            }
            
            Node node = Node.builder("group." + rankName).build();
            user.data().add(node);
            
            luckPerms.getUserManager().saveUser(user);
            
            plugin.getLogger().info("Granted rank '" + rankName + "' to " + discordName + " (UUID: " + uuid + ")");
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(plugin.getMessage("rank-granted", rankName));
                }
            });
        });
    }
    
    private void removeBoostRank(UUID uuid, String discordName) {
        String rankName = plugin.getBoostRank();
        
        CompletableFuture.runAsync(() -> {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            
            if (user == null) {
                plugin.getLogger().warning("Could not load LuckPerms user for UUID: " + uuid);
                return;
            }
            
            Node node = Node.builder("group." + rankName).build();
            user.data().remove(node);
            
            luckPerms.getUserManager().saveUser(user);
            
            plugin.getLogger().info("Removed rank '" + rankName + "' from " + discordName + " (UUID: " + uuid + ")");
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(plugin.getMessage("rank-removed", rankName));
                }
            });
        });
    }

    public void checkAllBoosters() {
        if (plugin.isDebug()) {
            plugin.getLogger().info("Running periodic boost check...");
        }
        
        Guild guild = DiscordUtil.getJda().getGuildById(DiscordSRV.getPlugin().getMainGuild().getId());
        if (guild == null) {
            plugin.getLogger().warning("Could not find Discord guild for periodic check!");
            return;
        }
        
        guild.loadMembers().onSuccess(members -> {
            int checked = 0;
            int granted = 0;
            int removed = 0;
            
            for (Member member : members) {
                UUID minecraftUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(member.getId());
                if (minecraftUuid == null) continue;
                
                checked++;
                boolean isBoosting = member.getTimeBoosted() != null;
                boolean hasRank = checkHasRank(minecraftUuid);
                
                if (isBoosting && !hasRank) {
                    grantBoostRank(minecraftUuid, member.getEffectiveName());
                    granted++;
                } else if (!isBoosting && hasRank && plugin.shouldRemoveOnUnboost()) {
                    removeBoostRank(minecraftUuid, member.getEffectiveName());
                    removed++;
                }
            }
            
            if (plugin.isDebug()) {
                plugin.getLogger().info("Periodic check completed: " + checked + " linked accounts checked, " 
                        + granted + " ranks granted, " + removed + " ranks removed");
            }
        });
    }
    
    private boolean checkHasRank(UUID uuid) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) return false;
            
            return user.getNodes().stream()
                    .anyMatch(node -> node.getKey().equals("group." + plugin.getBoostRank()));
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking rank for UUID " + uuid + ": " + e.getMessage());
            return false;
        }
    }
}
