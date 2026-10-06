package fr.eternom.eterEssential.module.staff;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Joueur visé par une commande du staff : soi-même, ou un joueur de CE serveur nommé en argument (avec la permission
 * eteressential.others.<commande>). Envoie le message d'erreur et renvoie null si ce n'est pas possible.
 */
final class StaffTarget {

    private StaffTarget() {
    }

    static Player resolve(CommandSender sender, String[] args, int index, String command, Messages messages) {
        if (args.length > index) {
            if (!sender.hasPermission("eteressential.others." + command)) {
                messages.send(sender, "staff.no-permission-others");
                return null;
            }
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                messages.send(sender, "player.not-here", "player", args[index]);
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "staff.console-needs-player");
        return null;
    }
}
