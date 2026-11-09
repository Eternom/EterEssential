package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.player.OnlineNames;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * /tpa [joueur | list], /tpahere [joueur], /tpaccept [joueur], /tpdeny [joueur], /tpacancel, /tptoggle [here],
 * /tpaignore [joueur], /tpaauto [joueur | clan], /tpaclan, /tpalog <joueur>.
 * /tpa ou /tpahere sans pseudo : le choix du joueur dans un menu (les têtes des joueurs connectés).
 */
public class TpaCommand implements TabExecutor {

    public enum Action { TPA, TPAHERE, ACCEPT, DENY, CANCEL, TOGGLE, IGNORE, AUTO, CLAN, LOG }

    public static final String LOG_PERMISSION = "eteressential.tpalog";

    private final TpaService tpa;
    private final OnlineNames names;
    private final Messages messages;
    private final Action action;
    /** Ouvre le choix d'un joueur pour une commande (« tpa {player} »). */
    private final BiConsumer<Player, String> picker;

    public TpaCommand(TpaService tpa, OnlineNames names, Messages messages, Action action, BiConsumer<Player, String> picker) {
        this.tpa = tpa;
        this.names = names;
        this.messages = messages;
        this.action = action;
        this.picker = picker;
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
                    picker.accept(player, action == Action.TPA ? "tpa {player}" : "tpahere {player}");
                } else if (action == Action.TPA && name.equalsIgnoreCase("list")) {
                    tpa.openRequests(player);
                } else {
                    tpa.request(player, name, action == Action.TPAHERE);
                }
            }
            case ACCEPT -> tpa.accept(player, name);
            case DENY -> tpa.deny(player, name);
            case CANCEL -> tpa.cancel(player);
            case TOGGLE -> tpa.toggle(player, name != null && name.equalsIgnoreCase("here"));
            case IGNORE -> tpa.toggleList(player, TpaLists.Kind.IGNORE, name);
            case AUTO -> tpa.toggleList(player, TpaLists.Kind.AUTO, name);
            case CLAN -> tpa.requestClan(player);
            case LOG -> {
                if (!player.hasPermission(LOG_PERMISSION)) {
                    messages.send(player, "tpa.log.no-permission");
                } else if (name == null) {
                    messages.send(player, "tpa.log.usage");
                } else {
                    tpa.showLog(player, name);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> completions = new ArrayList<>();
        switch (action) {
            case TPA, TPAHERE, IGNORE, AUTO, LOG -> completions.addAll(names.complete(args[0]));
            default -> {
            }
        }
        String typed = args[0].toLowerCase();
        if (action == Action.TPA && "list".startsWith(typed)) {
            completions.add("list");
        }
        if (action == Action.AUTO && "clan".startsWith(typed)) {
            completions.add("clan");
        }
        if (action == Action.TOGGLE && "here".startsWith(typed)) {
            completions.add("here");
        }
        return completions;
    }
}
