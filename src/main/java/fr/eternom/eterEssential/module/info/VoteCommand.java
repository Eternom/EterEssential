package fr.eternom.eterEssential.module.info;

import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /vote : un lien vers la page de vote du site (config.yml > vote.page). Jamais les sites de vote eux-mêmes : c'est la
 * page du site (Azuriom, plugin Vote) qui vérifie le vote et déclenche la récompense ; voté ailleurs, il ne compte pas.
 */
public class VoteCommand implements CommandExecutor {

    private final Messages messages;
    private final String page;

    public VoteCommand(Messages messages, String page) {
        this.messages = messages;
        this.page = page == null ? "" : page.trim();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (page.isEmpty()) {
            messages.send(sender, "vote.unset");
            return true;
        }
        messages.send(sender, "vote.info");
        Component button = messages.get(sender, "vote.button")
                .clickEvent(ClickEvent.openUrl(page))
                .hoverEvent(HoverEvent.showText(messages.get(sender, "vote.button-hover", "url", page)));
        sender.sendMessage(messages.prefix().append(button));
        messages.send(sender, "vote.how");
        return true;
    }
}
