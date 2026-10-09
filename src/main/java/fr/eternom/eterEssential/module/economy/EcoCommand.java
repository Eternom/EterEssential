package fr.eternom.eterEssential.module.economy;

import fr.eternom.eterLib.helper.economy.Money;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.player.OnlineNames;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import fr.eternom.eterEconomy.api.EconomyApi;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

/**
 * /eco <give|take|set|reset> <joueur> [montant] : gestion des soldes par le staff, aussi depuis la console
 * (récompenses de vote, boutique...). N'importe quel joueur déjà venu sur le réseau, même hors ligne.
 * Tout passe par Vault, en tâche de fond.
 */
public class EcoCommand implements TabExecutor {

    private static final List<String> ACTIONS = List.of("give", "take", "set", "reset");

    private final JavaPlugin plugin;
    private final PlayerDirectory directory;
    private final OnlineNames names;
    private final Messages messages;

    public EcoCommand(JavaPlugin plugin, PlayerDirectory directory, OnlineNames names, Messages messages) {
        this.plugin = plugin;
        this.directory = directory;
        this.names = names;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean needsAmount = !action.equals("reset");
        if (!ACTIONS.contains(action) || args.length != (needsAmount ? 3 : 2)) {
            messages.send(sender, "eco.usage");
            return true;
        }
        EconomyApi economy = EconomyApi.get().orElse(null);
        if (economy == null) {
            messages.send(sender, "economy.unavailable");
            return true;
        }
        double amount = needsAmount ? EconomyCommand.parseAmount(args[2], economy.fractionalDigits()) : 0;
        if (needsAmount && amount <= 0 && !(action.equals("set") && args[2].equals("0"))) {
            messages.send(sender, "pay.invalid-amount", "amount", args[2]);
            return true;
        }
        String name = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            Optional<NetworkPlayer> target = Optional.empty();
            try {
                target = directory.find(name);
                result = target.map(found -> apply(economy, Bukkit.getOfflinePlayer(found.uuid()), action, amount)).orElse(null);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "/eco " + action + " " + name, e);
                result = "error";
            }
            String outcome = result;
            String targetName = target.map(NetworkPlayer::name).orElse(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (outcome == null) {
                    messages.send(sender, "player.unknown", "player", name);
                } else if (outcome.equals("error")) {
                    messages.send(sender, "error.generic");
                } else if (outcome.equals("not-enough")) {
                    messages.send(sender, "eco.not-enough", "player", targetName);
                } else {
                    messages.send(sender, "eco." + action, "player", targetName, "amount", economy.format(amount),
                            "balance", outcome);
                }
            });
        });
        return true;
    }

    /** Bloquant. @return le nouveau solde mis en forme, ou "not-enough" si le retrait est impossible */
    private static String apply(EconomyApi economy, OfflinePlayer player, String action, double amount) {
        switch (action) {
            case "give" -> economy.deposit(player.getUniqueId(), amount, "EterEssential · staff (/eco)");
            case "take" -> {
                if (!economy.withdraw(player.getUniqueId(), amount, "EterEssential · staff (/eco)")) {
                    return "not-enough";
                }
            }
            default -> {
                // set / reset : Vault n'a pas de « fixer le solde », on ajoute ou retire la différence
                double target = action.equals("reset") ? 0 : amount;
                double difference = target - economy.balance(player.getUniqueId());
                if (difference > 0) {
                    economy.deposit(player.getUniqueId(), difference, "EterEssential · staff (/eco)");
                } else if (difference < 0) {
                    economy.withdraw(player.getUniqueId(), -difference, "EterEssential · staff (/eco)");
                }
            }
        }
        return economy.format(economy.balance(player.getUniqueId()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream().filter(action -> action.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return args.length == 2 ? names.complete(args[1]) : List.of();
    }
}
