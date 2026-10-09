package fr.eternom.eterEssential.module.info;

import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.OnlineNames;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * /list : joueurs en ligne sur tout le réseau, regroupés par serveur (sans les invisibles).
 * /find <joueur> : sur quel serveur il est. /seen <joueur> : en ligne, ou depuis quand il ne l'est plus.
 */
public class InfoCommand implements TabExecutor {

    public enum Action { LIST, FIND, SEEN }

    private final JavaPlugin plugin;
    private final PlayerDirectory directory;
    private final PlayerLookup lookup;
    private final OnlineNames names;
    private final Messages messages;
    private final Action action;

    public InfoCommand(JavaPlugin plugin, PlayerDirectory directory, PlayerLookup lookup, OnlineNames names, Messages messages, Action action) {
        this.plugin = plugin;
        this.directory = directory;
        this.lookup = lookup;
        this.names = names;
        this.messages = messages;
        this.action = action;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        if (action == Action.LIST) {
            list(player);
            return true;
        }
        if (args.length != 1) {
            messages.send(player, action == Action.FIND ? "find.usage" : "seen.usage");
            return true;
        }
        lookup.findAny(player, args[0], found -> found.ifPresentOrElse(target -> show(player, target),
                () -> messages.send(player, "player.unknown", "player", args[0])));
        return true;
    }

    private void list(Player player) {
        Tasks.async(plugin, player, directory::listOnline, online -> {
            // Serveur affiché -> pseudos, triés
            Map<String, List<String>> byServer = new TreeMap<>(online.stream().collect(Collectors.groupingBy(
                    found -> EterLib.get().getServerDisplayName(found.server()),
                    Collectors.mapping(NetworkPlayer::name, Collectors.toList()))));
            messages.send(player, "list.header", "count", String.valueOf(online.size()));
            byServer.forEach((server, players) -> player.sendMessage(messages.get(player, "list.server", "server", server,
                    "count", String.valueOf(players.size()), "players", players.stream().sorted().collect(Collectors.joining(", ")))));
        }, () -> messages.send(player, "error.generic"));
    }

    private void show(Player player, NetworkPlayer target) {
        if (target.isOnline()) {
            messages.send(player, action == Action.FIND ? "find.online" : "seen.online", "player", target.name(),
                    "server", EterLib.get().getServerDisplayName(target.server()));
            return;
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - target.lastSeen()) / 1000);
        messages.send(player, action == Action.FIND ? "find.offline" : "seen.offline", "player", target.name(),
                "time", EterLib.get().formatDuration(player, seconds));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return action != Action.LIST && args.length == 1 ? names.complete(args[0]) : List.of();
    }
}
