package com.aurora_vn.discordboostrank;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DiscordBoostRank extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;
    private BoostListener boostListener;
    private BoostChecker boostChecker;
    private GroupManager groupManager;
    private AutomationManager automationManager;
    private StatisticsManager statisticsManager;
    private BackupManager backupManager;
    private DiscordNotifier discordNotifier;
    private final List<BoostTier> boostTiers = new ArrayList<>();
    private final Map<UUID, Integer> linkReminders = new HashMap<>();
    private String boostRank;
    private boolean removeOnUnboost;
    private boolean debug;
    private boolean automationStarted;
    private int jdaRetryAttempts = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loadConfiguration();

        if (!hookLuckPerms()) {
            return;
        }

        if (!checkDiscordSRV()) {
            return;
        }

        initializeManagers();

        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().runTaskLater(this, this::hookJDA, 40L);

        getLogger().info("DiscordBoostRank v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (boostListener != null) {
            JDA jda = DiscordSRV.getPlugin().getJda();
            if (jda != null) {
                jda.removeEventListener(boostListener);
                getLogger().info("Unregistered Discord boost listener");
            }
        }

        if (automationManager != null) {
            automationManager.stop();
        }

        if (statisticsManager != null) {
            statisticsManager.save();
        }

        getLogger().info("DiscordBoostRank has been disabled!");
    }

    private boolean hookLuckPerms() {
        try {
            luckPerms = LuckPermsProvider.get();
            getLogger().info("Successfully hooked into LuckPerms v" + luckPerms.getPluginMetadata().getVersion());
            return true;
        } catch (Exception e) {
            getLogger().severe("═══════════════════════════════════════");
            getLogger().severe("  FAILED TO HOOK INTO LUCKPERMS!");
            getLogger().severe("  Please install LuckPerms v5.4 or higher");
            getLogger().severe("  Download: https://luckperms.net/download");
            getLogger().severe("═══════════════════════════════════════");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private boolean checkDiscordSRV() {
        if (DiscordSRV.getPlugin() == null) {
            getLogger().severe("═══════════════════════════════════════");
            getLogger().severe("  DISCORDSRV NOT FOUND!");
            getLogger().severe("  Please install DiscordSRV");
            getLogger().severe("  Download: https://www.spigotmc.org/resources/discordsrv.18494/");
            getLogger().severe("═══════════════════════════════════════");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        getLogger().info("Found DiscordSRV v" + DiscordSRV.getPlugin().getDescription().getVersion());
        return true;
    }

    private void initializeManagers() {
        groupManager = new GroupManager(this, luckPerms);
        boostChecker = new BoostChecker(this, luckPerms);
        automationManager = new AutomationManager(this);
        statisticsManager = new StatisticsManager(this);
        backupManager = new BackupManager(this);
        discordNotifier = new DiscordNotifier(this);

        getLogger().info("Initialized all managers");
    }

    private void hookJDA() {
        JDA jda = DiscordSRV.getPlugin().getJda();

        if (jda != null) {
            boostListener = new BoostListener(this, luckPerms);
            jda.addEventListener(boostListener);
            getLogger().info("Registered Discord boost listener");

            if (!automationStarted) {
                automationManager.start();
                automationStarted = true;
            }

            verifyGuildAccess();

            jdaRetryAttempts = 0;
        } else {
            jdaRetryAttempts++;
            if (jdaRetryAttempts < 10) {
                getLogger().warning("JDA not ready yet, retrying... (attempt " + jdaRetryAttempts + "/10)");
                getServer().getScheduler().runTaskLater(this, this::hookJDA, 40L);
            } else {
                getLogger().severe("═══════════════════════════════════════");
                getLogger().severe("  FAILED TO CONNECT TO DISCORD!");
                getLogger().severe("  Please check your DiscordSRV configuration");
                getLogger().severe("  Make sure the bot token is valid");
                getLogger().severe("═══════════════════════════════════════");
            }
        }
    }

    private void verifyGuildAccess() {
        try {
            Guild guild = DiscordUtil.getJda().getGuildById(DiscordSRV.getPlugin().getMainGuild().getId());
            if (guild != null) {
                getLogger().info("Connected to Discord guild: " + guild.getName());
                getLogger().info("Bot has access to " + guild.getMemberCount() + " members");
            } else {
                getLogger().warning("Could not access Discord guild. Check bot permissions!");
            }
        } catch (Exception e) {
            getLogger().warning("Error verifying guild access: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (getConfig().getBoolean("auto-check.on-player-join", true)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
                boostChecker.checkPlayer(uuid, false);
            }, 40L);
        }

        if (getConfig().getBoolean("link-reminder.enabled", true) &&
                getConfig().getBoolean("link-reminder.on-join", true)) {

            String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);
            if (discordId == null) {
                int reminderCount = linkReminders.getOrDefault(uuid, 0);
                int maxReminders = getConfig().getInt("link-reminder.max-reminders", 3);

                if (reminderCount < maxReminders) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (player.isOnline()) {
                            String message = getMessage("notifications.in-game.link-reminder");
                            player.sendMessage(message);
                            linkReminders.put(uuid, reminderCount + 1);
                        }
                    }, 60L);
                }
            }
        }
    }

    private void loadConfiguration() {
        reloadConfig();
        loadBoostTiers();

        boostRank = !boostTiers.isEmpty()
                ? boostTiers.get(0).getName()
                : getConfig().getString("boost-rank", "Booster");

        removeOnUnboost = getConfig().getBoolean("remove-on-unboost", true);
        debug = getConfig().getBoolean("debug", false);

        if (debug) {
            getLogger().info("Debug mode enabled");
        }

        getLogger().info("Loaded " + boostTiers.size() + " boost tiers");
    }

    private void loadBoostTiers() {
        boostTiers.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("boost-ranks");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection tier = section.getConfigurationSection(key);
                if (tier == null) continue;

                String name = tier.getString("name");
                int required = tier.getInt("required-boosts", 1);
                int priority = tier.getInt("priority", 1);

                if (name != null && !name.isEmpty()) {
                    boostTiers.add(new BoostTier(name, required, priority));
                    if (debug) {
                        getLogger().info("Loaded tier: " + name + " (requires " + required + " boosts, priority " + priority + ")");
                    }
                }
            }
        }

        if (boostTiers.isEmpty()) {
            String defaultRank = getConfig().getString("boost-rank", "Booster");
            boostTiers.add(new BoostTier(defaultRank, 1, 1));
            getLogger().info("Using default tier: " + defaultRank);
        }

        boostTiers.sort(Comparator.comparingInt(BoostTier::getPriority));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("boostrankadmin")) {
            return false;
        }

        if (!sender.hasPermission("discordboostrank.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "check":
                return handleCheck(sender, args);
            case "sync":
                return handleSync(sender);
            case "clearcache":
                return handleClearCache(sender, args);
            case "stats":
                return handleStats(sender);
            case "backup":
                return handleBackup(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand! Use /bra for help.");
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "  DiscordBoostRank Admin Commands");
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "/bra reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/bra check <player>" + ChatColor.GRAY + " - Check player's boost status");
        sender.sendMessage(ChatColor.YELLOW + "/bra sync" + ChatColor.GRAY + " - Sync all linked accounts");
        sender.sendMessage(ChatColor.YELLOW + "/bra clearcache [player]" + ChatColor.GRAY + " - Clear boost check cache");
        sender.sendMessage(ChatColor.YELLOW + "/bra stats" + ChatColor.GRAY + " - View plugin statistics");
        sender.sendMessage(ChatColor.YELLOW + "/bra backup" + ChatColor.GRAY + " - Create manual backup");
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
    }

    private boolean handleReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading configuration...");

        loadConfiguration();

        if (automationManager != null) {
            automationManager.stop();
            automationManager.start();
            automationStarted = true;
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Configuration reloaded successfully!");
        sender.sendMessage(ChatColor.GRAY + "Loaded " + boostTiers.size() + " boost tiers");
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /bra check <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online!");
            return true;
        }

        UUID uuid = target.getUniqueId();
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);

        if (discordId == null) {
            sender.sendMessage(ChatColor.RED + target.getName() + " has not linked their Discord account!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Checking boost status for " + target.getName() + "...");

        Guild guild = DiscordUtil.getJda().getGuildById(DiscordSRV.getPlugin().getMainGuild().getId());
        if (guild == null) {
            sender.sendMessage(ChatColor.RED + "Could not find Discord guild!");
            return true;
        }

        guild.retrieveMemberById(discordId).queue(member -> {
            boolean isBoosting = member.getTimeBoosted() != null;

            sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
            sender.sendMessage(ChatColor.GOLD + "  Boost Status: " + target.getName());
            sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
            sender.sendMessage(ChatColor.YELLOW + "Discord: " + ChatColor.WHITE + member.getEffectiveName());
            sender.sendMessage(ChatColor.YELLOW + "Discord ID: " + ChatColor.WHITE + discordId);
            sender.sendMessage(ChatColor.YELLOW + "Boosting: " + (isBoosting ? ChatColor.GREEN + "Yes ✓" : ChatColor.RED + "No ✗"));

            if (isBoosting) {
                sender.sendMessage(ChatColor.YELLOW + "Since: " + ChatColor.WHITE + member.getTimeBoosted());
            }

            sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
        }, error -> {
            sender.sendMessage(ChatColor.RED + "Could not retrieve Discord member: " + error.getMessage());
        });

        return true;
    }

    private boolean handleSync(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Starting full sync of all linked accounts...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boostChecker.checkAllLinkedAccounts();
            sender.sendMessage(ChatColor.GREEN + "✓ Full sync completed!");
        });

        return true;
    }

    private boolean handleClearCache(CommandSender sender, String[] args) {
        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }

            boostChecker.clearCache(target.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "✓ Cleared cache for " + target.getName());
        } else {
            boostChecker.clearAllCache();
            sender.sendMessage(ChatColor.GREEN + "✓ Cleared all cache");
        }

        return true;
    }

    private boolean handleStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "  DiscordBoostRank Statistics");
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "Plugin Version: " + ChatColor.WHITE + getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Boost Tiers: " + ChatColor.WHITE + boostTiers.size());
        sender.sendMessage(ChatColor.YELLOW + "Online Players: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size());

        int linkedCount = DiscordSRV.getPlugin().getAccountLinkManager().getLinkedAccounts().size();
        sender.sendMessage(ChatColor.YELLOW + "Linked Accounts: " + ChatColor.WHITE + linkedCount);

        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════════");
        return true;
    }

    private boolean handleBackup(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Creating backup...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            backupManager.createBackup();
            sender.sendMessage(ChatColor.GREEN + "✓ Backup created successfully!");
        });

        return true;
    }

    // Getters
    public String getBoostRank() {
        return boostRank;
    }

    public boolean shouldRemoveOnUnboost() {
        return removeOnUnboost;
    }

    public boolean isDebug() {
        return debug;
    }

    public BoostChecker getBoostChecker() {
        return boostChecker;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public List<BoostTier> getBoostTiers() {
        return Collections.unmodifiableList(boostTiers);
    }

    public String getMessage(String key, String rank) {
        String message = resolveMessage(key);
        return ChatColor.translateAlternateColorCodes('&', message.replace("{rank}", rank));
    }

    public String getMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', resolveMessage(key));
    }

    private String resolveMessage(String key) {
        String direct = getConfig().getString(key);
        if (direct != null) {
            return direct;
        }

        String notificationPath = getConfig().getString("notifications.in-game." + key);
        if (notificationPath != null) {
            return notificationPath;
        }

        String messagePath = getConfig().getString("messages." + key);
        if (messagePath != null) {
            return messagePath;
        }

        return "";
    }
}