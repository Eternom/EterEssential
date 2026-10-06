package fr.eternom.eterEssential.module.back;

import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.teleport.Destination;
import fr.eternom.eterLib.module.teleport.EterTeleportEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Retient la position de départ de chaque téléportation d'EterLib (home, tpa, rtp, /back lui-même...) et, avec une
 * permission à part, le lieu de la mort. Seulement pour les joueurs qui ont le droit d'utiliser /back.
 */
public class BackListener implements Listener {

    public static final String PERMISSION = "eteressential.back";
    public static final String DEATH_PERMISSION = "eteressential.back.death";

    private final JavaPlugin plugin;
    private final BackStore store;
    private final String serverName;

    public BackListener(JavaPlugin plugin, BackStore store, String serverName) {
        this.plugin = plugin;
        this.store = store;
        this.serverName = serverName;
    }

    @EventHandler
    public void onTeleport(EterTeleportEvent event) {
        if (event.getPlayer().hasPermission(PERMISSION)) {
            save(event.getPlayer(), event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getPlayer().hasPermission(DEATH_PERMISSION)) {
            save(event.getPlayer(), event.getPlayer().getLocation());
        }
    }

    private void save(Player player, Location from) {
        Destination position = Destination.at(serverName, from.getWorld().getName(), from.getX(), from.getY(), from.getZ(),
                from.getYaw(), from.getPitch(), "");
        Tasks.async(plugin, () -> store.save(player.getUniqueId(), position), "Position de /back non enregistrée pour "
                + player.getName());
    }
}
