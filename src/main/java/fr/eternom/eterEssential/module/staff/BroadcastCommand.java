package fr.eternom.eterEssential.module.staff;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /broadcast <message> : annonce sur tout le réseau (avec Redis), dans le format de chaque langue (broadcast.format).
 * Le texte du staff est lu en MiniMessage (couleurs, liens...) : commande réservée au staff.
 */
public class BroadcastCommand implements CommandExecutor {

    private static final String BROADCAST = "broadcast";

    private final NetworkBus bus;
    private final Messages messages;

    public BroadcastCommand(NetworkBus bus, Messages messages) {
        this.bus = bus;
        this.messages = messages;
        bus.on(BROADCAST, data -> show(data.get("text").getAsString()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "broadcast.usage");
            return true;
        }
        String text = String.join(" ", args);
        show(text);
        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        bus.publish(BROADCAST, data, () -> {
            if (bus.isNetworked()) {
                messages.send(sender, "network.failed");
            }
        });
        return true;
    }

    private void show(String text) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String raw = messages.raw(player, "broadcast.format");
            player.sendMessage(messages.render(raw == null ? "<message>" : raw,
                    Placeholder.component("message", MiniMessage.miniMessage().deserialize(text))));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f);
        }
        Bukkit.getConsoleSender().sendMessage(MiniMessage.miniMessage().deserialize(text));
    }
}
