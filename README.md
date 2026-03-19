# DiscordBoostRank

An addon plugin for DiscordSRV that automatically grants ranks to players when they boost your Discord server.

## Requirements

- **Minecraft Server**: Spigot/Paper 1.16+
- **Java**: 17+
- **Required Plugins**:
  - DiscordSRV (1.25.0+)
  - LuckPerms (5.4+)

## Installation

1. Build the plugin from source:
   ```bash
   ./gradlew build
   ```

2. The JAR file will be generated at: `build/libs/DiscordBoostRank-1.0.0.jar`

3. Copy the JAR file into your server's `plugins/` folder

4. Restart the server

5. Edit the configuration at `plugins/DiscordBoostRank/config.yml`

## Configuration

```yaml
# Rank/Group to grant when a player boosts the server
boost-rank: "Booster"

# Automatically remove the rank when the player stops boosting?
remove-on-unboost: true

# Messages
messages:
  rank-granted: "&aYou have received &e{rank} &arank for boosting our Discord server!"
  rank-removed: "&cYour &e{rank} &crank has been removed as you are no longer boosting the server."
  not-linked: "&cYou need to link your Discord account first! Use /discord link"
  
# Debug mode
debug: false

# Check boost status every X minutes
periodic-check-minutes: 30
```

## Creating the Boost Rank in LuckPerms

Before using the plugin, create the "Booster" group in LuckPerms:

```
/lp creategroup Booster
/lp group Booster meta setprefix "&d[Booster] "
/lp group Booster permission set some.permission.here
```

## Commands

- `/boostrankadmin` or `/bra` - Show help
- `/bra reload` - Reload configuration
- `/bra check <player>` - Check a player's boost status

**Permission**: `discordboostrank.admin`

## How It Works

1. Players link their Discord account to Minecraft via DiscordSRV (`/discord link`)
2. When a player boosts the Discord server, the plugin automatically:
   - Detects the boost event
   - Checks whether the Discord user is linked to a Minecraft account
   - Adds the "Booster" rank (or your configured name) in LuckPerms
   - Sends a notification to the player (if they are online)
3. When they stop boosting, the plugin automatically removes the rank (if `remove-on-unboost: true`)

## Periodic Check

The plugin includes a periodic check to ensure no boosts are missed:
- Default: Checks all boosters every 30 minutes
- If a player is boosting but missing the rank → rank is granted automatically
- If a player is not boosting but still has the rank → rank is removed automatically

## Debug Mode

Enable debug mode in the config to view detailed logs:
```yaml
debug: true
```

Logs will show:
- When someone boosts or unboosted
- When a rank is granted or removed
- When a periodic check runs

## Support

If you encounter any issues:
1. Check the console logs
2. Enable debug mode
3. Use `/bra check <player>` to verify a player's status

## License

MIT License - Free to use and modify
