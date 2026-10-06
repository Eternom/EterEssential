package fr.eternom.eterEssential.module.rtp;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /rtp : menu des mondes de ce serveur où partir au hasard. */
public class RtpCommand implements CommandExecutor {

    private final RtpService rtp;
    private final Messages messages;

    public RtpCommand(RtpService rtp, Messages messages) {
        this.rtp = rtp;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            rtp.openMenu(player);
        } else {
            messages.send(sender, "command.players-only");
        }
        return true;
    }
}
