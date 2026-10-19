package fr.eternom.eterEssential.module.death;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Se déconnecter pendant un combat contre un JOUEUR (pas un monstre ; durée : EterLib teleport.combat-tag) : mort sur
 * place, comme une vraie mort (objets et expérience au sol, perte d'argent, message public « combat-log »). La mort a
 * lieu en LOWEST, avant qu'EterSync enregistre l'inventaire (MONITOR) : il enregistre un inventaire vide, rien n'est
 * dupliqué. Pas de punition pour un joueur expulsé (redémarrage, staff). Dispense : eteressential.bypass.combatlog.
 * Déconnecté, il ne voit rien de sa mort : à sa prochaine arrivée (n'importe quel serveur), un message la lui explique
 * (Redis eteressential:combatlog:<uuid>, gardé 7 jours).
 */
public class CombatLog implements Listener {

    public static final String BYPASS = "eteressential.bypass.combatlog";
    private static final String KEY = "eteressential:combatlog:";
    private static final Duration REMEMBER = Duration.ofDays(7);
    /** Après l'arrivée : laisser passer les messages d'accueil. */
    private static final long REMINDER_DELAY_TICKS = 20 * 4;

    private final JavaPlugin plugin;
    private final RedisCache redis;
    private final Messages messages;

    /** Joueurs qui meurent en ce moment pour s'être déconnectés : leur adversaire (pour le message de mort). */
    private final Map<UUID, String> leaving = new ConcurrentHashMap<>();

    public CombatLog(JavaPlugin plugin, RedisCache redis, Messages messages) {
        this.plugin = plugin;
        this.redis = redis;
        this.messages = messages;
    }

    /** L'adversaire fui, si ce joueur meurt pour s'être déconnecté en combat (pendant sa mort seulement). */
    public String fledFrom(UUID player) {
        return leaving.get(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String opponent = EterLib.get().getCombat().opponent(player.getUniqueId()).orElse(null);
        if (opponent == null || event.getReason() == PlayerQuitEvent.QuitReason.KICKED || player.isDead()
                || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.hasPermission(BYPASS)) {
            return;
        }
        leaving.put(player.getUniqueId(), opponent);
        try {
            player.setHealth(0); // la mort de Minecraft : objets et expérience au sol, événement de mort pour les autres plugins
        } finally {
            leaving.remove(player.getUniqueId());
        }
        String key = KEY + player.getUniqueId();
        Tasks.async(plugin, () -> redis.set(key, opponent, REMEMBER), "Rappel de déconnexion en combat non noté pour " + player.getName());
    }

    /** Le fuyard revient : on lui dit ce qui s'est passé. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String key = KEY + player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Tasks.async(plugin, player, () -> {
                String opponent = redis.get(key).orElse(null);
                if (opponent != null) {
                    redis.delete(key);
                }
                return opponent;
            }, opponent -> {
                if (opponent != null) {
                    messages.send(player, "combat-log.reminder", "opponent", opponent);
                }
            }, () -> { });
        }, REMINDER_DELAY_TICKS);
    }
}
