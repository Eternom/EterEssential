package fr.eternom.eterEssential.module.staff;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /invsee <joueur> et /endersee <joueur> : ouvre l'inventaire ou le coffre de l'Ender d'un joueur de CE serveur
 * (l'inventaire d'un joueur ailleurs n'est pas chargé ici). Modifiable : réservé au staff.
 */
public class InventoryCommand implements TabExecutor {

    public enum Action { INVSEE, ENDERSEE }

    private final Messages messages;
    private final Action action;

    public InventoryCommand(Messages messages, Action action) {
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
            messages.send(staff, "player.not-here", "player", args[0]);
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
