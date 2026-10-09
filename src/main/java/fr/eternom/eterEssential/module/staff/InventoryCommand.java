package fr.eternom.eterEssential.module.staff;

import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * /invsee <joueur> et /endersee <joueur> : l'inventaire ou le coffre de l'Ender d'un joueur de CE serveur, modifiable
 * (réservé au staff). /invsee d'un joueur ailleurs ou hors ligne : sa dernière sauvegarde, en lecture seule (API
 * d'EterSync ; sans EterSync, rien).

 */
public class InventoryCommand implements TabExecutor {

    public enum Action { INVSEE, ENDERSEE }

    private final JavaPlugin plugin;
    private final PlayerLookup lookup;
    private final Messages messages;
    private final Action action;

    public InventoryCommand(JavaPlugin plugin, PlayerLookup lookup, Messages messages, Action action) {
        this.plugin = plugin;
        this.lookup = lookup;
        this.messages = messages;
        this.action = action;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(staff, action == Action.INVSEE ? "invsee.usage" : "endersee.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            if (action == Action.INVSEE && Bukkit.getPluginManager().isPluginEnabled("EterSync")) {
                RemoteInventory.open(plugin, lookup, messages, staff, args[0]);
            } else {
                messages.send(staff, "player.not-here", "player", args[0]);
            }
            return true;
        }
        staff.openInventory(action == Action.INVSEE ? target.getInventory() : target.getEnderChest());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
