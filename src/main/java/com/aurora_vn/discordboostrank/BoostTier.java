package com.aurora_vn.discordboostrank;

public class BoostTier {
    private final String name;
    private final int requiredBoosts;
    private final int priority;
    
    public BoostTier(String name, int requiredBoosts, int priority) {
        this.name = name;
        this.requiredBoosts = requiredBoosts;
        this.priority = priority;
    }
    
    public String getName() {
        return name;
    }
    
    public int getRequiredBoosts() {
        return requiredBoosts;
    }
    
    public int getPriority() {
        return priority;
    }
}
