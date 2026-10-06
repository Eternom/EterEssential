package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.player.OnlineNames;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel, /tptoggle. */
public class TpaCommand implements TabExecutor {

    public enum Action { TPA, TPAHERE, ACCEPT, DENY, CANCEL, TOGGLE }

    private final TpaService tpa;
    private final OnlineNames names;
    private final Messages messages;
    private final boolean networked;
    private final Action action;

    public TpaCommand(TpaService tpa, OnlineNames names, Messages messages, boolean networked, Action action) {
        this.tpa = tpa;
        this.names = names;
        this.messages = messages;
        this.networked = networked;
        this.action = action;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        String name = args.length > 0 ? args[0] : null;
        switch (action) {
            case TPA, TPAHERE -> {
                if (name == null) {
                    messages.send(player, action == Action.TPA ? "tpa.usage" : "tpa.usage-here");
                } else {
                    tpa.request(player, name, action == Action.TPAHERE);
                }
            }
            case ACCEPT -> tpa.accept(player, name);
            case DENY -> tpa.deny(player, name);
            case CANCEL -> tpa.cancel(player);
            case TOGGLE -> tpa.toggle(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean wantsName = action == Action.TPA || action == Action.TPAHERE;
        return wantsName && args.length == 1 ? names.complete(args[0], networked) : List.of();
    }
}
