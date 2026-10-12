package fr.eternom.eterEssential.module.furnace;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /furnace : ouvre le four portatif du joueur. */
public class FurnaceCommand implements CommandExecutor {

    private final PortableFurnace furnace;
    private final Messages messages;

    public FurnaceCommand(PortableFurnace furnace, Messages messages) {
        this.furnace = furnace;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        furnace.open(player);
        return true;
    }
}
