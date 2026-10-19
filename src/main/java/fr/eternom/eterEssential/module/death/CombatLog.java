package fr.eternom.eterEssential.module.death;

import fr.eternom.eterLib.EterLib;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Se déconnecter pendant un combat contre un JOUEUR (pas un monstre ; durée : EterLib teleport.combat-tag) : mort sur
 * place, comme une vraie mort (objets et expérience au sol, perte d'argent, message public « combat-log »). La mort a
 * lieu en LOWEST, avant qu'EterSync enregistre l'inventaire (MONITOR) : il enregistre un inventaire vide, rien n'est
 * dupliqué. Pas de punition pour un joueur expulsé (redémarrage, staff). Dispense : eteressential.bypass.combatlog.
 */
public class CombatLog implements Listener {

    public static final String BYPASS = "eteressential.bypass.combatlog";

    /** Joueurs qui meurent en ce moment pour s'être déconnectés : leur adversaire (pour le message de mort). */
    private final Map<UUID, String> leaving = new ConcurrentHashMap<>();

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
    }
}
