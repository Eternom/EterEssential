package fr.eternom.eterEssential.module.staff;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.player.OnlineNames;
import fr.eternom.eterLib.module.teleport.Destination;
import fr.eternom.eterLib.module.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Téléportations du staff, immédiates et sans règles (combat, délai, attente), sur tout le réseau :
 * /tp <joueur> (y aller), /tp <x> <y> <z> (dans son monde), /tphere <joueur> (le faire venir).
 * Pour /tphere vers un joueur d'un autre serveur, c'est son serveur qui le fait partir ("staff-tphere").
 */
public class StaffTeleportCommand implements TabExecutor {

    public enum Action { TP, TPHERE }

    private static final String TPHERE = "staff-tphere";

    private final TeleportService teleports;
    private final PlayerLookup lookup;
    private final NetworkBus bus;
    private final OnlineNames names;
    private final Messages messages;
    private final Action action;

    public StaffTeleportCommand(TeleportService teleports, PlayerLookup lookup, NetworkBus bus, OnlineNames names,
                                Messages messages, Action action) {
        this.teleports = teleports;
        this.lookup = lookup;
        this.bus = bus;
        this.names = names;
        this.messages = messages;
        this.action = action;
        if (action == Action.TPHERE) {
            bus.on(TPHERE, data -> {
                Player traveller = Bukkit.getPlayer(UUID.fromString(data.get("traveller").getAsString()));
                if (traveller != null) {
                    String staffName = data.get("label").getAsString();
                    teleports.teleportNow(traveller, Destination.toPlayer(UUID.fromString(data.get("target").getAsString()),
                            data.get("target_server").getAsString(), staffName));
                    messages.send(traveller, "tphere.by", "player", staffName);
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        if (action == Action.TP && args.length == 3) {
            coordinates(staff, args);
            return true;
        }
        if (args.length != 1) {
            messages.send(staff, action == Action.TP ? "tp.usage" : "tphere.usage");
            return true;
        }
        lookup.findOnline(staff, args[0], found -> {
            if (found.isEmpty()) {
                messages.send(staff, "player.offline", "player", args[0]);
                return;
            }
            PlayerLookup.OnlinePlayer target = found.get();
            if (target.uuid().equals(staff.getUniqueId())) {
                messages.send(staff, "tp.self");
                return;
            }
            if (action == Action.TP) {
                teleports.teleportNow(staff, Destination.toPlayer(target.uuid(), target.server(), target.name()));
                messages.send(staff, "tp.done", "player", target.name());
                return;
            }
            messages.send(staff, "tphere.done", "player", target.name());
            Player local = Bukkit.getPlayer(target.uuid());
            if (local != null) {
                teleports.teleportNow(local, Destination.toPlayer(staff.getUniqueId(), lookup.serverName(), staff.getName()));
                messages.send(local, "tphere.by", "player", staff.getName());
                return;
            }
            JsonObject data = new JsonObject();
            data.addProperty("traveller", target.uuid().toString());
            data.addProperty("target", staff.getUniqueId().toString());
            data.addProperty("target_server", lookup.serverName());
            data.addProperty("label", staff.getName());
            bus.publish(TPHERE, data, () -> messages.send(staff, "network.failed"));
        });
        return true;
    }

    /** /tp x y z : "~" garde la coordonnée actuelle, "~5" la décale. */
    private void coordinates(Player staff, String[] args) {
        Location current = staff.getLocation();
        try {
            double x = coordinate(args[0], current.getX());
            double y = coordinate(args[1], current.getY());
            double z = coordinate(args[2], current.getZ());
            teleports.teleportNow(staff, Destination.at(lookup.serverName(), current.getWorld().getName(), x, y, z,
                    current.getYaw(), current.getPitch(), args[0] + " " + args[1] + " " + args[2]));
        } catch (NumberFormatException e) {
            messages.send(staff, "tp.usage");
        }
    }

    private static double coordinate(String text, double current) {
        if (text.startsWith("~")) {
            return current + (text.length() > 1 ? Double.parseDouble(text.substring(1)) : 0);
        }
        return Double.parseDouble(text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? names.complete(args[0]) : List.of();
    }
}
