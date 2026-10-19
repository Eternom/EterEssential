package fr.eternom.eterEssential.module.death;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Se déconnecter pendant un combat contre un JOUEUR (pas un monstre) : mort sur place, comme une vraie mort (objets et
 * expérience au sol, perte d'argent, message public « combat-log »). La mort a lieu en LOWEST, avant qu'EterSync
 * enregistre l'inventaire (MONITOR) : il enregistre un inventaire vide, rien n'est dupliqué. Pas de punition pour un
 * joueur expulsé (redémarrage, staff). Dispense : eteressential.bypass.combatlog. Thread principal.
 */
public class CombatLog implements Listener {

    public static final String BYPASS = "eteressential.bypass.combatlog";

    /** Le dernier adversaire et la fin du combat. */
    private record Fight(String opponent, long until) {
    }

    private final long millis;
    private final Map<UUID, Fight> fights = new ConcurrentHashMap<>();
    /** Joueurs qui meurent en ce moment pour s'être déconnectés : leur adversaire (pour le message de mort). */
    private final Map<UUID, String> leaving = new ConcurrentHashMap<>();

    public CombatLog(int seconds) {
        this.millis = seconds * 1000L;
    }

    /** L'adversaire fui, si ce joueur meurt pour s'être déconnecté en combat (pendant sa mort seulement). */
    public String fledFrom(UUID player) {
        return leaving.get(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter
                : event.getDamager();
        if (!(event.getEntity() instanceof Player victim) || !(damager instanceof Player attacker) || victim == attacker) {
            return;
        }
        long until = System.currentTimeMillis() + millis;
        fights.put(victim.getUniqueId(), new Fight(attacker.getName(), until));
        fights.put(attacker.getUniqueId(), new Fight(victim.getName(), until));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Fight fight = fights.remove(player.getUniqueId());
        if (fight == null || fight.until() < System.currentTimeMillis() || event.getReason() == PlayerQuitEvent.QuitReason.KICKED
                || player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.hasPermission(BYPASS)) {
            return;
        }
        leaving.put(player.getUniqueId(), fight.opponent());
        try {
            player.setHealth(0); // la mort de Minecraft : objets et expérience au sol, événement de mort pour les autres plugins
        } finally {
            leaving.remove(player.getUniqueId());
        }
    }
}
