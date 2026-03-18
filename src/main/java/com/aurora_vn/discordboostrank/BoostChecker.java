package com.aurora_vn.discordboostrank;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.exceptions.ErrorResponseException;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BoostChecker {

    private final DiscordBoostRank plugin;
    private final LuckPerms luckPerms;
    private final Map<UUID, Long> lastCheckCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Set<UUID> unknownMembers = ConcurrentHashMap.newKeySet();

    public BoostChecker(DiscordBoostRank plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public void checkPlayer(UUID uuid, boolean notify) {
        if (unknownMembers.contains(uuid)) {
            if (plugin.isDebug()) {
                plugin.getLogger().fine("Skipping check for " + uuid + " (known to not be in Discord server)");
            }
            return;
        }

        if (plugin.getConfig().getBoolean("advanced.cache.enabled", true)) {
            Long lastCheck = lastCheckCache.get(uuid);
            if (lastCheck != null) {
                long cacheDuration = plugin.getConfig().getInt("advanced.cache.duration-minutes", 5) * 60L * 1000L;
                if (System.currentTimeMillis() - lastCheck < cacheDuration) {
                    if (plugin.isDebug()) {
                        plugin.getLogger().fine("[Cache] Skipping check for " + uuid + " (recently checked)");
                    }
                    return;
                }
            }
        }

        lastCheckCache.put(uuid, System.currentTimeMillis());

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);
        if (discordId == null) {
            if (plugin.isDebug()) {
                plugin.getLogger().fine("[Check] Player " + uuid + " has no linked Discord account");
            }
            return;
        }

        Guild guild = DiscordUtil.getJda().getGuildById(DiscordSRV.getPlugin().getMainGuild().getId());
        if (guild == null) {
            plugin.getLogger().warning("Could not find Discord guild!");
            return;
        }

        guild.retrieveMemberById(discordId).queue(
                member -> {
                    failedAttempts.remove(uuid);
                    unknownMembers.remove(uuid);
                    processBoostStatus(uuid, member, notify);
                },
                error -> {
                    handleMemberRetrievalError(uuid, discordId, error, notify);
                }
        );
    }

    private void handleMemberRetrievalError(UUID uuid, String discordId, Throwable error, boolean notify) {
        if (error instanceof ErrorResponseException) {
            ErrorResponseException ere = (ErrorResponseException) error;

            if (ere.getErrorCode() == 10007) {
                unknownMembers.add(uuid);
                failedAttempts.remove(uuid);

                if (plugin.isDebug()) {
                    plugin.getLogger().info("[Discord] Player " + uuid + " is not in Discord server, removing boost ranks if any");
                }

                removeAllBoostRanks(uuid, notify);
                return;
            }

            if (ere.getErrorCode() == 50001) {
                plugin.getLogger().warning("Bot missing access to retrieve member " + discordId);
                return;
            }
        }

        int attempts = failedAttempts.getOrDefault(uuid, 0) + 1;
        failedAttempts.put(uuid, attempts);

        int maxAttempts = plugin.getConfig().getInt("advanced.auto-retry.max-attempts", 3);

        if (attempts < maxAttempts) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("Failed to retrieve member " + uuid + " (attempt " + attempts + "/" + maxAttempts + "): " + error.getMessage());
            }

            int delay = plugin.getConfig().getInt("advanced.auto-retry.delay-seconds", 5);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                checkPlayer(uuid, notify);
            }, delay * 20L);
        } else {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("Max retries reached for " + uuid + ": " + error.getMessage());
            }
            failedAttempts.remove(uuid);
        }
    }

    private void removeAllBoostRanks(UUID uuid, boolean notify) {
        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(uuid);

        userFuture.thenAccept(user -> {
            if (user == null) {
                return;
            }

            Set<String> currentBoostRanks = getCurrentBoostRanks(user);

            if (!currentBoostRanks.isEmpty()) {
                for (String rank : currentBoostRanks) {
                    removeRank(uuid, user, rank, notify);
                }

                luckPerms.getUserManager().saveUser(user).thenRun(() -> {
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("[Rank Update] Removed all boost ranks from " + uuid + " (not in Discord server)");
                    }
                });
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error removing boost ranks for " + uuid + ": " + throwable.getMessage());
            return null;
        });
    }

    private void processBoostStatus(UUID uuid, Member member, boolean notify) {
        boolean isBoosting = member.getTimeBoosted() != null;
        int boostCount = isBoosting ? 1 : 0;

        if (plugin.isDebug()) {
            plugin.getLogger().info("[Boost Check] " + member.getEffectiveName() + " (" + uuid + "): " +
                    (isBoosting ? "Boosting" : "Not boosting"));
        }

        BoostTier tier = getAppropriateTier(boostCount);

        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(uuid);

        userFuture.thenAccept(user -> {
            if (user == null) {
                plugin.getLogger().warning("Could not load LuckPerms user: " + uuid);
                return;
            }

            Set<String> currentBoostRanks = getCurrentBoostRanks(user);
            String targetRank = tier != null ? tier.getName() : null;

            boolean changed = false;

            if (targetRank != null && !currentBoostRanks.contains(targetRank)) {
                grantRank(uuid, user, targetRank, boostCount, notify);
                changed = true;
            }

            if (plugin.getConfig().getBoolean("advanced.auto-fix-conflicts", true)) {
                for (String rank : currentBoostRanks) {
                    if (!rank.equals(targetRank)) {
                        removeRank(uuid, user, rank, notify);
                        changed = true;
                    }
                }
            }

            if (targetRank == null && !currentBoostRanks.isEmpty() &&
                    plugin.getConfig().getBoolean("auto-remove.enabled", true)) {
                for (String rank : currentBoostRanks) {
                    removeRank(uuid, user, rank, notify);
                    changed = true;
                }
            }

            if (changed) {
                luckPerms.getUserManager().saveUser(user).thenRun(() -> {
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("[Rank Update] Changes saved for " + uuid);
                    }

                    if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
                        plugin.getStatisticsManager().recordBoostChange(uuid, targetRank, boostCount);
                    }
                });
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error processing boost status for " + uuid + ": " + throwable.getMessage());
            return null;
        });
    }

    private BoostTier getAppropriateTier(int boostCount) {
        if (boostCount <= 0) return null;

        List<BoostTier> tiers = plugin.getBoostTiers();
        BoostTier bestTier = null;

        for (BoostTier tier : tiers) {
            if (boostCount >= tier.getRequiredBoosts()) {
                if (bestTier == null || tier.getPriority() < bestTier.getPriority()) {
                    bestTier = tier;
                }
            }
        }

        return bestTier;
    }

    private Set<String> getCurrentBoostRanks(User user) {
        Set<String> ranks = new HashSet<>();
        List<BoostTier> tiers = plugin.getBoostTiers();

        for (BoostTier tier : tiers) {
            boolean hasRank = user.getNodes().stream()
                    .anyMatch(node -> node.getKey().equals("group." + tier.getName()));

            if (hasRank) {
                ranks.add(tier.getName());
            }
        }

        return ranks;
    }

    private void grantRank(UUID uuid, User user, String rank, int boosts, boolean notify) {
        if (plugin.getConfig().getBoolean("auto-create-groups.enabled", true)) {
            plugin.getGroupManager().ensureGroupExists(rank);
        }

        Node node = Node.builder("group." + rank).build();
        user.data().add(node);

        plugin.getLogger().info("Granted rank '" + rank + "' to " + uuid + " (" + boosts + " boosts)");

        if (notify) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    String message = plugin.getMessage("notifications.in-game.rank-granted")
                            .replace("{rank}", rank)
                            .replace("{boosts}", String.valueOf(boosts));
                    player.sendMessage(message);

                    if (plugin.getConfig().getBoolean("auto-rewards.enabled", true)) {
                        List<String> commands = plugin.getConfig().getStringList("auto-rewards.on-grant");
                        for (String cmd : commands) {
                            String finalCmd = cmd.replace("{player}", player.getName())
                                    .replace("{rank}", rank)
                                    .replace("{boosts}", String.valueOf(boosts));

                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to execute reward command: " + finalCmd);
                            }
                        }
                    }
                }
            });
        }

        if (plugin.getConfig().getBoolean("notifications.discord.enabled", false)) {
            plugin.getDiscordNotifier().sendRankGranted(uuid, rank, boosts);
        }
    }

    private void removeRank(UUID uuid, User user, String rank, boolean notify) {
        Node node = Node.builder("group." + rank).build();
        user.data().remove(node);

        plugin.getLogger().info("Removed rank '" + rank + "' from " + uuid);

        if (notify && plugin.getConfig().getBoolean("auto-remove.notify-before-removal", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    String message = plugin.getMessage("notifications.in-game.rank-removed")
                            .replace("{rank}", rank);
                    player.sendMessage(message);

                    if (plugin.getConfig().getBoolean("auto-rewards.enabled", true)) {
                        List<String> commands = plugin.getConfig().getStringList("auto-rewards.on-remove");
                        for (String cmd : commands) {
                            String finalCmd = cmd.replace("{player}", player.getName())
                                    .replace("{rank}", rank);

                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to execute removal command: " + finalCmd);
                            }
                        }
                    }
                }
            });
        }

        if (plugin.getConfig().getBoolean("notifications.discord.enabled", false)) {
            plugin.getDiscordNotifier().sendRankRemoved(uuid, rank);
        }
    }

    public void checkAllLinkedAccounts() {
        Map<String, UUID> linkedAccounts = DiscordSRV.getPlugin().getAccountLinkManager().getLinkedAccounts();

        if (linkedAccounts.isEmpty()) {
            plugin.getLogger().info("No linked accounts to check");
            return;
        }

        plugin.getLogger().info("Checking " + linkedAccounts.size() + " linked accounts...");

        List<UUID> uuids = new ArrayList<>(linkedAccounts.values());
        int batchSize = 10;
        int delayMs = 1000;

        for (int i = 0; i < uuids.size(); i += batchSize) {
            int batchIndex = i / batchSize;
            int start = i;
            int end = Math.min(i + batchSize, uuids.size());

            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                for (int j = start; j < end; j++) {
                    checkPlayer(uuids.get(j), false);
                }

                if (plugin.isDebug()) {
                    plugin.getLogger().info("[Full Sync] Processed batch " + (batchIndex + 1) +
                            " (" + end + "/" + uuids.size() + ")");
                }
            }, (long) (batchIndex * delayMs) / 50L);
        }

        plugin.getLogger().info("Scheduled checks for all " + uuids.size() + " accounts");
    }

    public void clearCache(UUID uuid) {
        lastCheckCache.remove(uuid);
        failedAttempts.remove(uuid);
        unknownMembers.remove(uuid);
    }

    public void clearAllCache() {
        lastCheckCache.clear();
        failedAttempts.clear();
        unknownMembers.clear();
        plugin.getLogger().info("Cleared all boost check cache");
    }

    public int getUnknownMemberCount() {
        return unknownMembers.size();
    }

    public void cleanupUnknownMembers() {
        Map<String, UUID> linkedAccounts = DiscordSRV.getPlugin().getAccountLinkManager().getLinkedAccounts();
        Set<UUID> linkedUUIDs = new HashSet<>(linkedAccounts.values());

        unknownMembers.removeIf(uuid -> !linkedUUIDs.contains(uuid));

        if (plugin.isDebug()) {
            plugin.getLogger().info("Cleaned up unknown members list, now tracking " + unknownMembers.size() + " users not in Discord");
        }
    }
}