package fr.eternom.eterEssential.module.economy;

import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterLib.helper.economy.Money;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.OnlineNames;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import fr.eternom.eterEconomy.api.EconomyApi;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * /money [joueur] et /pay <joueur> <montant>, par l'API d'EterEconomy. Le destinataire peut être hors ligne ou sur un
 * autre serveur : il est prévenu s'il est connecté quelque part. Les appels à l'économie se font en tâche de fond.
 */
public class EconomyCommand implements TabExecutor {

    public static final String OTHERS_PERMISSION = "eteressential.others.money";

    public enum Action { MONEY, PAY }

    private enum PayResult { OK, NOT_ENOUGH, FAILED }

    private final JavaPlugin plugin;
    private final PlayerLookup lookup;
    private final NetworkBus bus;
    private final OnlineNames names;
    private final Messages messages;
    private final Action action;

    public EconomyCommand(JavaPlugin plugin, PlayerLookup lookup, NetworkBus bus, OnlineNames names, Messages messages,
                          Action action) {
        this.plugin = plugin;
        this.lookup = lookup;
        this.bus = bus;
        this.names = names;
        this.messages = messages;
        this.action = action;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        // Lu à chaque fois : EterEconomy peut être chargé après nous ou rechargé
        EconomyApi economy = EconomyApi.get().orElse(null);
        if (economy == null) {
            messages.send(player, "economy.unavailable");
            return true;
        }
        if (action == Action.MONEY) {
            money(player, economy, args);
        } else {
            pay(player, economy, args);
        }
        return true;
    }

    private void money(Player player, EconomyApi economy, String[] args) {
        if (args.length == 0 || !player.hasPermission(OTHERS_PERMISSION)) {
            Tasks.async(plugin, player, () -> economy.format(economy.balance(player.getUniqueId())),
                    balance -> messages.send(player, "money.self", "amount", balance),
                    () -> messages.send(player, "error.generic"));
            return;
        }
        lookup.findAny(player, args[0], found -> found.ifPresentOrElse(target -> Tasks.async(plugin, player,
                        () -> economy.format(economy.balance(target.uuid())),
                        balance -> messages.send(player, "money.other", "player", target.name(), "amount", balance),
                        () -> messages.send(player, "error.generic")),
                () -> messages.send(player, "player.unknown", "player", args[0])));
    }

    private void pay(Player player, EconomyApi economy, String[] args) {
        if (args.length != 2) {
            messages.send(player, "pay.usage");
            return;
        }
        double amount = parseAmount(args[1], economy.fractionalDigits());
        if (amount <= 0) {
            messages.send(player, "pay.invalid-amount", "amount", args[1]);
            return;
        }
        lookup.findAny(player, args[0], found -> {
            if (found.isEmpty()) {
                messages.send(player, "player.unknown", "player", args[0]);
                return;
            }
            NetworkPlayer target = found.get();
            if (target.uuid().equals(player.getUniqueId())) {
                messages.send(player, "pay.self");
                return;
            }
            String formatted = economy.format(amount);
            Tasks.async(plugin, player, () -> transfer(economy, player, offline(target), amount), result -> {
                switch (result) {
                    case OK -> {
                        messages.send(player, "pay.sent", "player", target.name(), "amount", formatted);
                        bus.notify(target.uuid(), "pay.received", true, "player", player.getName(), "amount", formatted);
                    }
                    case NOT_ENOUGH -> messages.send(player, "pay.not-enough", "amount", formatted);
                    case FAILED -> messages.send(player, "pay.failed");
                }
            }, () -> messages.send(player, "error.generic"));
        });
    }

    /** Bloquant. Retire puis verse ; si le versement échoue, l'expéditeur est remboursé. */
    private static PayResult transfer(EconomyApi economy, OfflinePlayer from, OfflinePlayer to, double amount) {
        if (!economy.withdraw(from.getUniqueId(), amount, "EterEssential · /pay")) {
            return economy.has(from.getUniqueId(), amount) ? PayResult.FAILED : PayResult.NOT_ENOUGH;
        }
        if (!economy.deposit(to.getUniqueId(), amount, "EterEssential · /pay")) {
            economy.deposit(from.getUniqueId(), amount, "EterEssential · /pay");
            return PayResult.FAILED;
        }
        return PayResult.OK;
    }

    /** "12,5" ou "12.5", arrondi aux décimales de la monnaie ; 0 si invalide. */
    static double parseAmount(String text, int fractionalDigits) {
        try {
            BigDecimal value = new BigDecimal(text.replace(',', '.'));
            if (fractionalDigits >= 0) {
                value = value.setScale(fractionalDigits, RoundingMode.DOWN);
            }
            double amount = value.doubleValue();
            return Double.isFinite(amount) && amount > 0 ? amount : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static OfflinePlayer offline(NetworkPlayer player) {
        return Bukkit.getOfflinePlayer(player.uuid());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean wantsName = action == Action.PAY || sender.hasPermission(OTHERS_PERMISSION);
        return wantsName && args.length == 1 ? names.complete(args[0]) : List.of();
    }
}
