package fr.eternom.eterEssential.module.menu;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Accroupi + touche « changer de main » (Maj + F par défaut) : ouvre /menu, sans changer les objets de main.
 * Chaque joueur peut le couper (/menu shortcut) : le choix est gardé sur le joueur (PDC eteressential:menu_shortcut).
 */
public class MenuShortcut implements Listener {

    private static final NamespacedKey OFF = new NamespacedKey("eteressential", "menu_shortcut_off");

    /** true si le joueur a coupé le raccourci. */
    public static boolean disabled(Player player) {
        return player.getPersistentDataContainer().has(OFF, PersistentDataType.BYTE);
    }

    /** Coupe ou remet le raccourci ; true s'il est maintenant actif. */
    public static boolean toggle(Player player) {
        if (disabled(player)) {
            player.getPersistentDataContainer().remove(OFF);
            return true;
        }
        player.getPersistentDataContainer().set(OFF, PersistentDataType.BYTE, (byte) 1);
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && player.hasPermission("eteressential.menu") && !disabled(player)) {
            event.setCancelled(true);
            player.performCommand("menu");
        }
    }
}
