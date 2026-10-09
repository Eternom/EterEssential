package fr.eternom.eterEssential.module.death;

import fr.eternom.eterLib.helper.economy.Money;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterEconomy.api.EconomyApi;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mourir coûte un pourcentage de son argent (death.money-loss-percent) : un pourcentage, pour que les grosses fortunes
 * le sentent aussi. L'argent disparaît (un évier de l'économie). Ce qui est rangé dans la banque d'un clan n'est pas
 * touché : c'est l'intérêt de la banque. Dispense : eteressential.bypass.deathloss.
 */
public class DeathPenalty implements Listener {

    public static final String BYPASS = "eteressential.bypass.deathloss";

    private final JavaPlugin plugin;
    private final Messages messages;
    private final double percent;

    public DeathPenalty(JavaPlugin plugin, Messages messages, double percent) {
        this.plugin = plugin;
        this.messages = messages;
        this.percent = percent;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EconomyApi economy = EconomyApi.get().orElse(null);
        if (percent <= 0 || economy == null || player.hasPermission(BYPASS)) {
            return;
        }
        Tasks.async(plugin, player, () -> {
            // Arrondi à l'unité inférieure : un petit solde (moins de 100 / percent) ne perd rien
            double loss = Math.floor(economy.balance(player.getUniqueId()) * percent / 100);
            return loss > 0 && economy.withdraw(player.getUniqueId(), loss, "EterEssential · mort") ? loss : 0;
        }, loss -> {
            if (loss > 0) {
                messages.send(player, "death.money-lost", "amount", Money.format(loss),
                        "percent", String.valueOf(percent).replaceAll("\\.0$", ""));
            }
        }, () -> {
        });
    }
}
