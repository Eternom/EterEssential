package fr.eternom.eterEssential.module.station;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;

import java.util.function.Consumer;

/**
 * /craft, /anvil, /smithing, /grindstone, /stonecutter, /enderchest : le menu du bloc, sans le bloc. Des commandes
 * d'usage (rien n'y reste) : elles peuvent être données pour une durée, par un grade ou le pass.
 */
public class StationCommand implements CommandExecutor {

    /**
     * Les établis sont rattachés à la position du joueur, sans vérifier le bloc : à la fermeture, ce qui reste dans la
     * grille lui est rendu, comme avec le vrai bloc (sans position, le jeu ne saurait pas où le rendre et le perdrait).
     */
    public enum Station {
        CRAFT(player -> player.openInventory(MenuType.CRAFTING.builder().location(player.getLocation()).checkReachable(false).build(player))),
        ANVIL(player -> player.openInventory(MenuType.ANVIL.builder().location(player.getLocation()).checkReachable(false).build(player))),
        SMITHING(player -> player.openInventory(MenuType.SMITHING.builder().location(player.getLocation()).checkReachable(false).build(player))),
        GRINDSTONE(player -> player.openInventory(MenuType.GRINDSTONE.builder().location(player.getLocation()).checkReachable(false).build(player))),
        STONECUTTER(player -> player.openInventory(MenuType.STONECUTTER.builder().location(player.getLocation()).checkReachable(false).build(player))),
        /** Son propre coffre de l'Ender, le même que celui du bloc. */
        ENDERCHEST(player -> player.openInventory(player.getEnderChest()));

        private final Consumer<Player> open;

        Station(Consumer<Player> open) {
            this.open = open;
        }
    }

    private final Messages messages;
    private final Station station;

    public StationCommand(Messages messages, Station station) {
        this.messages = messages;
        this.station = station;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        station.open.accept(player);
        return true;
    }
}
