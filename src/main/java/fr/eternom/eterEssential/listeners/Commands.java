package fr.eternom.eterEssential.listeners;

import fr.eternom.eterEssential.Main;
import fr.eternom.eterEssential.module.back.BackCommand;
import fr.eternom.eterEssential.module.economy.EcoCommand;
import fr.eternom.eterEssential.module.economy.EconomyCommand;
import fr.eternom.eterEssential.module.furnace.FurnaceCommand;
import fr.eternom.eterEssential.module.info.InfoCommand;
import fr.eternom.eterEssential.module.info.VoteCommand;
import fr.eternom.eterEssential.module.menu.MenuCommand;
import fr.eternom.eterEssential.module.network.LobbyCommand;
import fr.eternom.eterEssential.module.staff.InventoryCommand;
import fr.eternom.eterEssential.module.staff.PlayerActionCommand;
import fr.eternom.eterEssential.module.staff.StaffTeleportCommand;
import fr.eternom.eterEssential.module.station.StationCommand.Station;
import fr.eternom.eterEssential.module.station.StationCommand;
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
        MenuCommand menus = new MenuCommand(main, messages);
        java.util.function.BiConsumer<org.bukkit.entity.Player, String> picker = menus::pickPlayer;
        for (TpaCommand.Action action : TpaCommand.Action.values()) {
            register(main, switch (action) {
                case TPA -> "tpa";
                case TPAHERE -> "tpahere";
                case ACCEPT -> "tpaccept";
                case DENY -> "tpdeny";
                case CANCEL -> "tpacancel";
                case TOGGLE -> "tptoggle";
                case IGNORE -> "tpaignore";
                case AUTO -> "tpaauto";
                case CLAN -> "tpaclan";
                case LOG -> "tpalog";
            }, new TpaCommand(main.getTpa(), names, messages, action, picker));
        }
        register(main, "back", new BackCommand(main, main.getBack(), teleports, messages));

        // Menu central (sous-menus)
        register(main, "menu", menus);
        register(main, "lobby", new LobbyCommand(main, messages));
        fr.eternom.eterEssential.module.welcome.BooksCommand books = new fr.eternom.eterEssential.module.welcome.BooksCommand(main, lib.getRedis(), messages);
        register(main, "welcome", books);
        register(main, "rules", books);
        main.getServer().getPluginManager().registerEvents(books, main);
        main.getServer().getPluginManager().registerEvents(new fr.eternom.eterEssential.module.welcome.FirstTimeTips(main, messages), main);
        // /help : les commandes de ce serveur par catégorie ; /news : les nouveautés (annoncées une fois à la connexion)
        register(main, "help", new fr.eternom.eterEssential.module.help.HelpCommand(main, messages));
        fr.eternom.eterEssential.module.help.News news = new fr.eternom.eterEssential.module.help.News(main, lib.getRedis(), messages);
        register(main, "news", news);
        main.getServer().getPluginManager().registerEvents(news, main);
        register(main, "where", new fr.eternom.eterEssential.module.info.WhereCommand(messages));
        register(main, "vote", new VoteCommand(messages, main.getConfig().getString("vote.page")));

        // Établis et coffre de l'Ender portatifs, four portatif : des commandes d'usage, à donner par grade ou pour une durée
        register(main, "craft", new StationCommand(messages, Station.CRAFT));
        register(main, "anvil", new StationCommand(messages, Station.ANVIL));
        register(main, "smithing", new StationCommand(messages, Station.SMITHING));
        register(main, "grindstone", new StationCommand(messages, Station.GRINDSTONE));
        register(main, "stonecutter", new StationCommand(messages, Station.STONECUTTER));
        register(main, "enderchest", new StationCommand(messages, Station.ENDERCHEST));
        register(main, "furnace", new FurnaceCommand(main.getFurnace(), messages));

        // Informations
        register(main, "list", new InfoCommand(main, lib.getPlayers(), main.getLookup(), names, messages, InfoCommand.Action.LIST));
        register(main, "find", new InfoCommand(main, lib.getPlayers(), main.getLookup(), names, messages, InfoCommand.Action.FIND));
        register(main, "seen", new InfoCommand(main, lib.getPlayers(), main.getLookup(), names, messages, InfoCommand.Action.SEEN));

        // Économie (Vault -> EterEconomy)
        register(main, "money", new EconomyCommand(main, main.getLookup(), main.getBus(), names, messages, EconomyCommand.Action.MONEY, 0));
        register(main, "pay", new EconomyCommand(main, main.getLookup(), main.getBus(), names, messages, EconomyCommand.Action.PAY,
                main.getConfig().getDouble("pay.min-playtime-hours", 5)));
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
        register(main, "invsee", new InventoryCommand(main, main.getLookup(), messages, InventoryCommand.Action.INVSEE));
        register(main, "endersee", new InventoryCommand(main, main.getLookup(), messages, InventoryCommand.Action.ENDERSEE));
    }

    private void register(Main main, String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(main.getCommand(name), "Commande absente du plugin.yml : " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor instanceof TabCompleter completer ? completer : (sender, cmd, label, args) -> List.of());
    }
}
