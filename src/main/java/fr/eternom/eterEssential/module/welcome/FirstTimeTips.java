package fr.eternom.eterEssential.module.welcome;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Une astuce la première fois qu'un joueur fait une chose (une seule fois chacune, retenue sur le joueur, PDC
 * eteressential:tips) :
 * <ul>
 *   <li>bed : premier lit posé → /sethome ;</li>
 *   <li>chest : premier coffre posé hors d'un terrain de clan (EterClan) → il n'est pas protégé ;</li>
 *   <li>job : première bûche, culture mûre ou minerai, sans métier (EterJob) → ça rapporterait avec un métier.</li>
 * </ul>
 * Texte : lang/ > tip.&lt;nom&gt;. config.yml > tips: false pour les couper.
 */
public class FirstTimeTips implements Listener {

    private static final NamespacedKey DONE = new NamespacedKey("eteressential", "tips");

    private final JavaPlugin plugin;
    private final Messages messages;

    public FirstTimeTips(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlockPlaced().getType();
        if (Tag.BEDS.isTagged(type)) {
            tip(player, "bed");
        } else if ((type == Material.CHEST || type == Material.BARREL) && Bukkit.getPluginManager().isPluginEnabled("EterClan")
                && !Clans.claimed(event.getBlockPlaced())) {
            tip(player, "chest");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (done(player, "job") || !Bukkit.getPluginManager().isPluginEnabled("EterJob") || !workBlock(event.getBlock())) {
            return;
        }
        Tasks.async(plugin, player, () -> Jobs.none(player), none -> {
            if (none) {
                tip(player, "job");
            }
        }, () -> { });
    }

    /** Un bloc qu'un métier paierait : bûche, culture mûre, minerai. */
    private static boolean workBlock(Block block) {
        Material type = block.getType();
        if (Tag.LOGS.isTagged(type) || type.name().endsWith("_ORE")) {
            return true;
        }
        return Tag.CROPS.isTagged(type) && block.getBlockData() instanceof Ageable crop && crop.getAge() >= crop.getMaximumAge();
    }

    private void tip(Player player, String name) {
        if (player.getGameMode() == GameMode.CREATIVE || !plugin.getConfig().getBoolean("tips", true) || done(player, name)) {
            return;
        }
        Set<String> done = read(player);
        done.add(name);
        player.getPersistentDataContainer().set(DONE, PersistentDataType.STRING, String.join(",", done));
        messages.send(player, "tip." + name);
    }

    private static boolean done(Player player, String name) {
        return read(player).contains(name);
    }

    private static Set<String> read(Player player) {
        String raw = player.getPersistentDataContainer().get(DONE, PersistentDataType.STRING);
        return raw == null || raw.isBlank() ? new HashSet<>() : new HashSet<>(Arrays.asList(raw.split(",")));
    }

    /** Lu seulement si EterClan tourne. */
    private static final class Clans {
        static boolean claimed(Block block) {
            return fr.eternom.eterClan.api.ClanApi.get().map(api -> api.isClaimed(block.getLocation())).orElse(true);
        }
    }

    /** Lu seulement si EterJob tourne. Bloquant. */
    private static final class Jobs {
        static boolean none(Player player) {
            return fr.eternom.eterJob.api.JobApi.get().map(api -> api.job(player.getUniqueId()).isEmpty()).orElse(false);
        }
    }
}
