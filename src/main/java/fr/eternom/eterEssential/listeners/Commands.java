package fr.eternom.eterEssential.listeners;

import fr.eternom.eterEssential.Main;
import fr.eternom.eterEssential.module.back.BackCommand;
import fr.eternom.eterEssential.module.economy.EcoCommand;
import fr.eternom.eterEssential.module.economy.EconomyCommand;
import fr.eternom.eterEssential.module.info.InfoCommand;
import fr.eternom.eterEssential.module.staff.BroadcastCommand;
import fr.eternom.eterEssential.module.staff.InventoryCommand;
import fr.eternom.eterEssential.module.staff.PlayerActionCommand;
import fr.eternom.eterEssential.module.staff.StaffTeleportCommand;
import fr.eternom.eterEssential.module.tpa.TpaCommand;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.player.OnlineNames;
import fr.eternom.eterLib.module.teleport.TeleportService;
import org.bukkit.GameMode;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Objects;

public class Commands {

    public Commands(Main main) {
        EterLib lib = EterLib.get();
        Messages messages = main.getMessages();
        OnlineNames names = lib.getOnlineNames();
        TeleportService teleports = lib.getTeleports();

        // Téléportation entre joueurs, /back
        register(main, "tpa", new TpaCommand(main.getTpa(), names, messages, TpaCommand.Action.TPA));
        register(main, "tpahere", new TpaCommand(main.getTpa(), names, messages, TpaCommand.Action.TPAHERE));
        register(main, "tpaccept", new TpaCommand(main.getTpa(), names, messages, TpaCommand.Action.ACCEPT));
        register(main, "tpdeny", new TpaCommand(main.getTpa(), names, messages, TpaCommand.Action.DENY));
        register(main, "tpacancel", new TpaCommand(main.getTpa(), names, messages, TpaCommand.Action.CANCEL));
        register(main, "tptoggle", new TpaCommand(main.getTpa(), names, messages, TpaCommand.Action.TOGGLE));
        register(main, "back", new BackCommand(main, main.getBack(), teleports, messages));

        // Informations
        register(main, "list", new InfoCommand(main, lib.getPlayers(), main.getLookup(), names, messages, InfoCommand.Action.LIST));
        register(main, "find", new InfoCommand(main, lib.getPlayers(), main.getLookup(), names, messages, InfoCommand.Action.FIND));
        register(main, "seen", new InfoCommand(main, lib.getPlayers(), main.getLookup(), names, messages, InfoCommand.Action.SEEN));

        // Économie (Vault -> EterEconomy)
        register(main, "money", new EconomyCommand(main, main.getLookup(), main.getBus(), names, messages, EconomyCommand.Action.MONEY));
        register(main, "pay", new EconomyCommand(main, main.getLookup(), main.getBus(), names, messages, EconomyCommand.Action.PAY));
        register(main, "eco", new EcoCommand(main, lib.getPlayers(), names, messages));

        // Staff
        register(main, "gm", new PlayerActionCommand(messages, PlayerActionCommand.Action.GAMEMODE, null));
        register(main, "gmc", new PlayerActionCommand(messages, PlayerActionCommand.Action.GAMEMODE, GameMode.CREATIVE));
        register(main, "gms", new PlayerActionCommand(messages, PlayerActionCommand.Action.GAMEMODE, GameMode.SURVIVAL));
        register(main, "gma", new PlayerActionCommand(messages, PlayerActionCommand.Action.GAMEMODE, GameMode.ADVENTURE));
        register(main, "gmsp", new PlayerActionCommand(messages, PlayerActionCommand.Action.GAMEMODE, GameMode.SPECTATOR));
        register(main, "fly", new PlayerActionCommand(messages, PlayerActionCommand.Action.FLY, null));
        register(main, "heal", new PlayerActionCommand(messages, PlayerActionCommand.Action.HEAL, null));
        register(main, "feed", new PlayerActionCommand(messages, PlayerActionCommand.Action.FEED, null));
        register(main, "clear", new PlayerActionCommand(messages, PlayerActionCommand.Action.CLEAR, null));
        register(main, "speed", new PlayerActionCommand(messages, PlayerActionCommand.Action.SPEED, null));
        register(main, "tp", new StaffTeleportCommand(teleports, main.getLookup(), main.getBus(), names, messages, StaffTeleportCommand.Action.TP));
        register(main, "tphere", new StaffTeleportCommand(teleports, main.getLookup(), main.getBus(), names, messages, StaffTeleportCommand.Action.TPHERE));
        register(main, "invsee", new InventoryCommand(messages, InventoryCommand.Action.INVSEE));
        register(main, "endersee", new InventoryCommand(messages, InventoryCommand.Action.ENDERSEE));
        register(main, "broadcast", new BroadcastCommand(main.getBus(), messages));
    }

    private void register(Main main, String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(main.getCommand(name), "Commande absente du plugin.yml : " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor instanceof TabCompleter completer ? completer : (sender, cmd, label, args) -> List.of());
    }
}
