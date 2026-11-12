package fr.eternom.eterEssential.module.help;

import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /help [catégorie] : les commandes du serveur où l'on est, par catégorie (config.yml > help.categories), cliquables.
 * Seules celles qui existent ici et que le joueur a le droit d'utiliser sont montrées : une liste toujours juste. Le
 * texte d'une commande : lang/ help.command.<commande>, sinon la description de son plugin.yml.
 */
public class HelpCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final Messages messages;

    public HelpCommand(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("help.categories");
        if (categories == null) {
            messages.send(sender, "help.empty");
            return true;
        }
        String only = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null;
        messages.send(sender, "help.header");
        boolean any = false;
        for (String category : categories.getKeys(false)) {
            if (only != null && !category.equals(only)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            for (String name : categories.getStringList(category)) {
                Command target = Bukkit.getCommandMap().getCommand(name);
                if (target == null || !target.testPermissionSilent(sender)) {
                    continue;
                }
                lines.add(line(sender, name, target));
            }
            if (lines.isEmpty()) {
                continue;
            }
            any = true;
            sender.sendMessage(messages.get(sender, "help.category", "category", messages.plain(sender, "help.categories." + category)));
            lines.forEach(sender::sendMessage);
        }
        if (!any) {
            messages.send(sender, "help.empty");
        }
        return true;
    }

    /** « /homes · tes homes », clic : la commande prête à envoyer. */
    private Component line(CommandSender sender, String name, Command target) {
        String own = messages.raw(sender, "help.command." + name);
        String description = own != null ? messages.plain(sender, "help.command." + name)
                : target instanceof PluginCommand pluginCommand ? pluginCommand.getDescription() : target.getDescription();
        return messages.get(sender, "help.line", TagResolver.resolver(Placeholder.unparsed("description", description == null ? "" : description)),
                        "command", name)
                .clickEvent(ClickEvent.suggestCommand("/" + name + " "))
                .hoverEvent(HoverEvent.showText(messages.get(sender, "help.hover", "command", name)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("help.categories");
        if (args.length != 1 || categories == null) {
            return List.of();
        }
        return categories.getKeys(false).stream().filter(key -> key.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
