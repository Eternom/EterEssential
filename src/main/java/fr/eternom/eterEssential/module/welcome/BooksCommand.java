package fr.eternom.eterEssential.module.welcome;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deux livres, relisibles à tout moment (et depuis /menu > Paramètres) :
 * <ul>
 *   <li>/welcome : le guide du serveur (menu, Premiers pas, métiers, monde ressource, homes, clans, aide) ;</li>
 *   <li>/rules : les règles, d'après les motifs de sanction de la modération ; /rules accept les accepte.</li>
 * </ul>
 * Pages : lang/ > welcome.page-1, -2... et rules.page-1... (jusqu'à la première absente).
 * <p>
 * L'acceptation est gardée dans Redis, COMMUNE au réseau (un changement de serveur ne redemande rien), pendant
 * rules.reconfirm-days jours. Ensuite (ou si Redis l'a perdue), un rappel simple revient : les règles évoluent parfois,
 * les relire et les accepter de nouveau. Un rappel non suivi ne revient pas avant rules.remind-hours heures, même en
 * changeant de serveur. Rien n'est bloqué. Le PDC eteressential:rules_accepted (ancien stockage, par serveur) sert
 * seulement à choisir le rappel « déjà accepté une fois ».
 */
public class BooksCommand implements TabExecutor, Listener {

    private static final NamespacedKey ACCEPTED = new NamespacedKey("eteressential", "rules_accepted");
    private static final int MAX_PAGES = 20;
    private static final long JOIN_DELAY_TICKS = 20 * 12;

    private final JavaPlugin plugin;
    private final RedisCache redis;
    private final Messages messages;
    /** Joueurs connectés ici qui ont accepté (lu dans Redis à l'arrivée). */
    private final Set<UUID> accepted = ConcurrentHashMap.newKeySet();

    public BooksCommand(JavaPlugin plugin, RedisCache redis, Messages messages) {
        this.plugin = plugin;
        this.redis = redis;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        if (command.getName().equals("welcome")) {
            open(player, "welcome");
        } else if (args.length > 0 && args[0].equalsIgnoreCase("accept")) {
            accept(player);
        } else {
            open(player, "rules");
            if (!accepted.contains(player.getUniqueId())) {
                remind(player);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return command.getName().equals("rules") && args.length == 1 && "accept".startsWith(args[0].toLowerCase()) ? List.of("accept")
                : List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            boolean remindedRecently;
            try {
                ok = redis.exists(acceptedKey(uuid));
                // Un seul rappel par période, même en changeant de serveur
                remindedRecently = !ok && !redis.setIfAbsent(remindedKey(uuid), "1", Duration.ofHours(remindHours()));
            } catch (RuntimeException e) {
                return; // Redis indisponible : pas de rappel plutôt qu'un rappel à chaque serveur
            }
            if (ok) {
                accepted.add(uuid);
                return;
            }
            if (remindedRecently) {
                return;
            }
            // Après les messages d'arrivée (accueil, guide des nouveaux)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !accepted.contains(uuid)) {
                    remind(player);
                }
            }, JOIN_DELAY_TICKS);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        accepted.remove(event.getPlayer().getUniqueId());
    }

    private void accept(Player player) {
        UUID uuid = player.getUniqueId();
        accepted.add(uuid);
        player.getPersistentDataContainer().set(ACCEPTED, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                redis.set(acceptedKey(uuid), "1", Duration.ofDays(reconfirmDays()));
                redis.delete(remindedKey(uuid));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Acceptation des règles non gardée pour " + player.getName() + " : " + e.getMessage());
            }
        });
        messages.send(player, "rules.accepted");
    }

    /** Premier rappel, ou « les règles évoluent parfois » pour qui les a déjà acceptées une fois. */
    private void remind(Player player) {
        boolean before = player.getPersistentDataContainer().has(ACCEPTED, PersistentDataType.BYTE);
        messages.send(player, before ? "rules.remind-again" : "rules.remind");
    }

    private long reconfirmDays() {
        return Math.max(1, plugin.getConfig().getLong("rules.reconfirm-days", 30));
    }

    private long remindHours() {
        return Math.max(1, plugin.getConfig().getLong("rules.remind-hours", 12));
    }

    private static String acceptedKey(UUID player) {
        return "eteressential:rules-accepted:" + player;
    }

    private static String remindedKey(UUID player) {
        return "eteressential:rules-reminded:" + player;
    }

    private void open(Player player, String book) {
        List<Component> pages = new ArrayList<>();
        for (int i = 1; i <= MAX_PAGES && messages.raw(player, book + ".page-" + i) != null; i++) {
            pages.add(messages.get(player, book + ".page-" + i));
        }
        if (pages.isEmpty()) {
            return;
        }
        player.openBook(Book.book(messages.get(player, book + ".title"), Component.text("Eternom"), pages));
    }
}
