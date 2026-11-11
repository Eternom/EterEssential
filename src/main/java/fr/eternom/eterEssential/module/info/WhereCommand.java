package fr.eternom.eterEssential.module.info;

import fr.eternom.eterClan.api.ClanApi;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /where : où le joueur se trouve, en quelques lignes : serveur, monde et coordonnées, biome, et à qui est le terrain
 * (terrain d'un clan où il peut construire ou non, ou nature libre ; EterClan facultatif).
 */
public class WhereCommand implements TabExecutor {

    private final Messages messages;

    public WhereCommand(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        Location at = player.getLocation();
        messages.send(player, "where.header");
        player.sendMessage(messages.get(player, "where.server", "server", EterLib.get().getServerDisplayName()));
        player.sendMessage(messages.get(player, "where.world", "world",
                messages.plain(player, "where.dimension." + at.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT)),
                "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()), "z", String.valueOf(at.getBlockZ())));
        player.sendMessage(messages.get(player, "where.biome",
                Placeholder.component("biome", Component.translatable(at.getBlock().getBiome().translationKey()))));
        if (Bukkit.getPluginManager().isPluginEnabled("EterClan")) {
            player.sendMessage(messages.get(player, Land.status(player, at)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }

    /** Lu seulement si EterClan tourne (sinon NoClassDefFoundError). */
    private static final class Land {
        static String status(Player player, Location at) {
            return ClanApi.get().map(api -> !api.isClaimed(at) ? "where.wild"
                    : api.canBuild(player, at) ? "where.claim-build" : "where.claim-locked").orElse("where.wild");
        }
    }
}
