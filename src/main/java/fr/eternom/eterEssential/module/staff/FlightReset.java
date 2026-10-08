package fr.eternom.eterEssential.module.staff;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Le droit de voler est gardé dans les données du joueur sur chaque serveur : un /fly d'un staff, ou un plugin de lobby
 * installé par erreur (double saut d'EterHub), le laisserait voler en survie pour toujours. À la connexion, en survie ou
 * en aventure, il le perd sauf avec eteressential.fly. Au plus tôt (LOWEST) : le double saut d'un lobby le redonne après.
 */
public class FlightReset implements Listener {

    private static final String PERMISSION = "eteressential.fly";

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && player.getAllowFlight() && !player.hasPermission(PERMISSION)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }
}
