package fr.eternom.eterEssential.module.staff;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Commandes du staff sur soi ou sur un joueur de ce serveur : /fly, /heal, /feed, /clear, /gm (gmc, gms, gma, gmsp)
 * et /speed. Le joueur visé est prévenu si ce n'est pas soi.
 */
public class PlayerActionCommand implements TabExecutor {

    public enum Action { FLY, HEAL, FEED, CLEAR, GAMEMODE, SPEED }

    private final Messages messages;
    private final Action action;
    private final GameMode fixedMode; // /gmc, /gms... ; null pour /gm <mode>

    public PlayerActionCommand(Messages messages, Action action, GameMode fixedMode) {
        this.messages = messages;
        this.action = action;
        this.fixedMode = fixedMode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (action) {
            case GAMEMODE -> gamemode(sender, args);
            case SPEED -> speed(sender, args);
            default -> {
                Player target = StaffTarget.resolve(sender, args, 0, permissionName(), messages);
                if (target != null) {
                    apply(target);
                    confirm(sender, target, action.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return true;
    }

    private void apply(Player target) {
        switch (action) {
            case FLY -> {
                target.setAllowFlight(!target.getAllowFlight());
                if (!target.getAllowFlight()) {
                    target.setFlying(false);
                }
            }
            case HEAL -> {
                target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
                target.setFireTicks(0);
                target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
            }
            case FEED -> {
                target.setFoodLevel(20);
                target.setSaturation(20);
            }
            case CLEAR -> target.getInventory().clear();
            default -> {
            }
        }
    }

    private void gamemode(CommandSender sender, String[] args) {
        int targetIndex = fixedMode == null ? 1 : 0;
        GameMode mode = fixedMode != null ? fixedMode : args.length > 0 ? parseMode(args[0]) : null;
        if (mode == null) {
            messages.send(sender, "gamemode.usage");
            return;
        }
        Player target = StaffTarget.resolve(sender, args, targetIndex, "gamemode", messages);
        if (target != null) {
            target.setGameMode(mode);
            String name = messages.plain(target, "gamemode." + mode.name().toLowerCase(Locale.ROOT));
            sendDone(sender, target, "gamemode.done", "gamemode.done-other", "gamemode.done-by", name);
        }
    }

    private void speed(CommandSender sender, String[] args) {
        int speed;
        try {
            speed = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        } catch (NumberFormatException e) {
            speed = -1;
        }
        if (speed < 1 || speed > 10) {
            messages.send(sender, "speed.usage");
            return;
        }
        Player target = StaffTarget.resolve(sender, args, 1, "speed", messages);
        if (target == null) {
            return;
        }
        // Vitesses de Minecraft : marche 0,2 et vol 0,1 par défaut, 1 au maximum
        boolean flying = target.isFlying();
        float value = flying ? speed / 10f : Math.min(1f, 0.2f + (speed - 1) * 0.8f / 9f);
        if (flying) {
            target.setFlySpeed(value);
        } else {
            target.setWalkSpeed(value);
        }
        sendDone(sender, target, flying ? "speed.fly" : "speed.walk", flying ? "speed.fly-other" : "speed.walk-other",
                flying ? "speed.fly-by" : "speed.walk-by", String.valueOf(speed));
    }

    private void confirm(CommandSender sender, Player target, String key) {
        String state = action == Action.FLY
                ? messages.plain(target, target.getAllowFlight() ? "state.enabled" : "state.disabled")
                : "";
        sendDone(sender, target, key + ".done", key + ".done-other", key + ".done-by", state);
    }

    /** Message à soi, ou au staff et au joueur visé. value : <value> dans les textes (état, mode, vitesse). */
    private void sendDone(CommandSender sender, Player target, String self, String other, String by, String value) {
        if (sender.equals(target)) {
            messages.send(target, self, "value", value);
            return;
        }
        messages.send(sender, other, "player", target.getName(), "value", value);
        messages.send(target, by, "player", sender.getName(), "value", value);
    }

    private String permissionName() {
        return action.name().toLowerCase(Locale.ROOT);
    }

    private static GameMode parseMode(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival", "survie" -> GameMode.SURVIVAL;
            case "1", "c", "creative", "creatif", "créatif" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "aventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "spectateur" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        int targetIndex = switch (action) {
            case GAMEMODE -> fixedMode == null ? 1 : 0;
            case SPEED -> 1;
            default -> 0;
        };
        if (action == Action.GAMEMODE && fixedMode == null && args.length == 1) {
            return List.of("survival", "creative", "adventure", "spectator").stream()
                    .filter(mode -> mode.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == targetIndex + 1) {
            String prefix = args[targetIndex].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
