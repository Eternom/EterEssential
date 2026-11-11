package fr.eternom.eterEssential.module.welcome;

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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Deux livres, relisibles à tout moment (et depuis /menu > Paramètres) :
 * <ul>
 *   <li>/welcome : le guide du serveur (menu, Premiers pas, métiers, monde ressource, homes, clans, aide) ;</li>
 *   <li>/rules : les règles, d'après les motifs de sanction de la modération ; /rules accept les accepte.</li>
 * </ul>
 * Pages : lang/ > welcome.page-1, -2... et rules.page-1... (jusqu'à la première absente). Tant qu'un joueur n'a pas
 * accepté les règles, un rappel avec deux boutons arrive à chaque connexion ; rien n'est bloqué. Acceptation gardée
 * sur le joueur (PDC eteressential:rules_accepted).
 */
public class BooksCommand implements TabExecutor, Listener {

    private static final NamespacedKey ACCEPTED = new NamespacedKey("eteressential", "rules_accepted");
    private static final int MAX_PAGES = 20;

    private final JavaPlugin plugin;
    private final Messages messages;

    public BooksCommand(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
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
            player.getPersistentDataContainer().set(ACCEPTED, PersistentDataType.BYTE, (byte) 1);
            messages.send(player, "rules.accepted");
        } else {
            open(player, "rules");
            if (!accepted(player)) {
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
        if (accepted(player)) {
            return;
        }
        // Après les messages d'arrivée (accueil, guide des nouveaux)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !accepted(player)) {
                remind(player);
            }
        }, 20 * 12);
    }

    private static boolean accepted(Player player) {
        return player.getPersistentDataContainer().has(ACCEPTED, PersistentDataType.BYTE);
    }

    /** « Lis les règles » avec deux boutons : [Lire] (/rules) et [J'accepte] (/rules accept). */
    private void remind(Player player) {
        messages.send(player, "rules.remind");
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
