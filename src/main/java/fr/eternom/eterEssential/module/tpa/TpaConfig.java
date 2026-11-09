package fr.eternom.eterEssential.module.tpa;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Les réglages du tpa (config.yml > tpa). */
public record TpaConfig(Duration ttl, Duration hereTtl, int maxReceived, Duration spam, Set<String> blockedWorlds,
                        boolean claimsClanOnly, boolean clanNoWarmup, boolean refuseTargetInCombat, double costTpa,
                        double costTpaHere, int logDays, int alertTargets, Duration alertWindow, Duration clanCooldown) {

    public static TpaConfig load(ConfigurationSection section) {
        ConfigurationSection config = section == null ? new org.bukkit.configuration.MemoryConfiguration() : section;
        int ttl = Math.max(10, config.getInt("expire-seconds", 60));
        return new TpaConfig(
                Duration.ofSeconds(ttl),
                Duration.ofSeconds(Math.max(10, config.getInt("expire-seconds-here", ttl))),
                Math.max(1, config.getInt("max-received", 5)),
                Duration.ofSeconds(Math.max(0, config.getInt("spam-seconds", 10))),
                lower(config.getStringList("blocked-worlds")),
                config.getBoolean("claims-clan-only", false),
                config.getBoolean("clan-no-warmup", true),
                config.getBoolean("refuse-target-in-combat", true),
                Math.max(0, config.getDouble("cost.tpa", 0)),
                Math.max(0, config.getDouble("cost.tpahere", 0)),
                Math.max(1, config.getInt("log-days", 30)),
                Math.max(0, config.getInt("alert.targets", 5)),
                Duration.ofMinutes(Math.max(1, config.getInt("alert.minutes", 10))),
                Duration.ofMinutes(Math.max(0, config.getInt("clan-cooldown-minutes", 5))));
    }

    public boolean isBlocked(String world) {
        return world != null && blockedWorlds.contains(world.toLowerCase(Locale.ROOT));
    }

    private static Set<String> lower(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
