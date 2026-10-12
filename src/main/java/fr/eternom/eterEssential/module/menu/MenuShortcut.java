package fr.eternom.eterEssential.module.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Accroupi + touche « changer de main » (Maj + F par défaut) : ouvre /menu, sans changer les objets de main. */
public class MenuShortcut implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission("eteressential.menu")) {
            event.setCancelled(true);
            event.getPlayer().performCommand("menu");
        }
    }
}
