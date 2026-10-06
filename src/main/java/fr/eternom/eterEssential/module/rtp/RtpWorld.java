package fr.eternom.eterEssential.module.rtp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Un monde proposé dans le menu /rtp (config.yml > rtp.worlds) : arrivée entre minRadius et maxRadius blocs
 * autour du point d'apparition du monde.
 */
public record RtpWorld(String world, Material icon, int minRadius, int maxRadius) {

    /** Lit rtp.worlds ; une entrée invalide est ignorée avec un avertissement. */
    public static List<RtpWorld> load(ConfigurationSection section, Logger logger) {
        List<RtpWorld> worlds = new ArrayList<>();
        if (section == null) {
            return worlds;
        }
        for (String world : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(world);
            if (entry == null) {
                continue;
            }
            Material icon = Material.matchMaterial(entry.getString("icon", "GRASS_BLOCK").toUpperCase(Locale.ROOT));
            int min = Math.max(0, entry.getInt("min-radius", 500));
            int max = entry.getInt("max-radius", 5000);
            if (icon == null || !icon.isItem() || max <= min) {
                logger.warning("rtp.worlds." + world + " ignoré : icône inconnue ou max-radius <= min-radius");
                continue;
            }
            worlds.add(new RtpWorld(world, icon, min, max));
        }
        return worlds;
    }
}
