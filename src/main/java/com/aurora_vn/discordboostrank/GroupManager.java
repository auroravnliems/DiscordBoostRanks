package com.aurora_vn.discordboostrank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.bukkit.ChatColor;

import java.util.List;

public class GroupManager {
    
    private final DiscordBoostRank plugin;
    private final LuckPerms luckPerms;
    
    public GroupManager(DiscordBoostRank plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public void ensureGroupExists(String groupName) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        
        if (group == null) {
            plugin.getLogger().info("[Auto-Create] Creating group: " + groupName);
            
            luckPerms.getGroupManager().createAndLoadGroup(groupName).thenAccept(createdGroup -> {
                List<String> permissions = plugin.getConfig().getStringList("auto-create-groups.default-permissions");
                for (String perm : permissions) {
                    Node node = Node.builder(perm).build();
                    createdGroup.data().add(node);
                }
                
                String prefix = plugin.getConfig().getString("auto-create-groups.prefixes." + groupName);
                if (prefix != null) {
                    prefix = ChatColor.translateAlternateColorCodes('&', prefix);
                    Node prefixNode = Node.builder("prefix.100." + prefix).build();
                    createdGroup.data().add(prefixNode);
                }
                
                luckPerms.getGroupManager().saveGroup(createdGroup);
                
                plugin.getLogger().info("[Auto-Create] Successfully created group: " + groupName);
            });
        }
    }
}
