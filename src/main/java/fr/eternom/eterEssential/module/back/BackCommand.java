package fr.eternom.eterEssential.module.back;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.teleport.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** /back : retour à la dernière position quittée (téléportation, ou mort avec la permission dédiée), même sur un autre serveur. */
public class BackCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final BackStore store;
    private final TeleportService teleports;
    private final Messages messages;

    public BackCommand(JavaPlugin plugin, BackStore store, TeleportService teleports, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.teleports = teleports;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        UUID uuid = player.getUniqueId();
        String destinationLabel = messages.plain(player, "back.label");
        Tasks.async(plugin, player, () -> store.get(uuid, destinationLabel),
                found -> found.ifPresentOrElse(destination -> teleports.teleport(player, destination),
                        () -> messages.send(player, "back.none")),
                () -> messages.send(player, "error.generic"));
        return true;
    }
}
