package fr.eternom.eterEssential.module.welcome;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Le tout premier passage d'un joueur sur le réseau : un titre et l'essentiel pour démarrer (menu, Premiers pas,
 * métier, wiki), et une annonce aux autres pour qu'ils l'accueillent. Une seule fois sur tout le réseau
 * (eteressential_newcomers, INSERT IGNORE) et seulement pour une première connexion récente (eter_players.first_seen) :
 * les joueurs déjà là avant ce module ne sont jamais pris pour des nouveaux. Annonce : config newcomers.announce
 * (network, server ou none).
 */
public class Newcomers implements Listener {

    private static final String TABLE = "newcomers";
    private static final String BUS_TYPE = "newcomer";
    /** Première connexion plus ancienne : un joueur déjà connu. */
    private static final long RECENT_MILLIS = Duration.ofMinutes(10).toMillis();
    /** Après l'arrivée : EterLib a noté la première connexion, et les messages d'accueil sont passés. */
    private static final long DELAY_TICKS = 3 * 20;
    private static final List<String> GUIDE = List.of("newcomer.guide-intro", "newcomer.guide-menu", "newcomer.guide-steps",
            "newcomer.guide-job", "newcomer.guide-wiki");

    private final JavaPlugin plugin;
    private final Database database;
    private final Messages messages;
    private final NetworkBus bus;
    private final String announce;

    public Newcomers(JavaPlugin plugin, Database database, Messages messages, NetworkBus bus, String announce) {
        this.plugin = plugin;
        this.database = database;
        this.messages = messages;
        this.bus = bus;
        this.announce = announce.toLowerCase(Locale.ROOT);
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("name", Column.Type.STRING).length(16).notNull(),
                Column.of("joined_at", Column.Type.LONG).notNull());
        bus.on(BUS_TYPE, data -> announce(data.get("player").getAsString()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Tasks.async(plugin, player, () -> isNew(player.getUniqueId(), player.getName()), fresh -> {
                    if (fresh) {
                        welcome(player);
                    }
                }, () -> { });
            }
        }, DELAY_TICKS);
    }

    /** Bloquant. true une seule fois par joueur, sur tout le réseau, et seulement s'il vient d'arriver. */
    private boolean isNew(UUID player, String name) {
        long firstSeen = EterLib.get().getPlayers().get(player).map(NetworkPlayer::firstSeen).orElse(0L);
        if (firstSeen < System.currentTimeMillis() - RECENT_MILLIS) {
            return false;
        }
        return database.execute("INSERT IGNORE INTO " + database.table(TABLE) + " (uuid, name, joined_at) VALUES (?, ?, ?)",
                player, name, System.currentTimeMillis()) > 0;
    }

    private void welcome(Player player) {
        player.showTitle(Title.title(messages.get(player, "newcomer.title"), messages.get(player, "newcomer.subtitle")));
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        GUIDE.forEach(key -> player.sendMessage(messages.get(player, key, "player", player.getName())));
        if (announce.equals("none")) {
            return;
        }
        announce(player.getName());
        if (announce.equals("network")) {
            JsonObject data = new JsonObject();
            data.addProperty("player", player.getName());
            bus.publish(BUS_TYPE, data);
        }
    }

    /** Aux joueurs de ce serveur (le nouveau compris : il voit qu'on l'accueille). */
    private void announce(String name) {
        Bukkit.getOnlinePlayers().forEach(online -> messages.send(online, "newcomer.announce", "player", name));
    }
}
