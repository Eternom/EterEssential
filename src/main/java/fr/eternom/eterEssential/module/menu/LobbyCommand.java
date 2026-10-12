package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /lobby lancé par le serveur (bouton du /menu) : le vrai /lobby est une commande du proxy, qu'un menu ne peut pas
 * lancer ; on demande donc au proxy le même retour au lobby (canal eter:lobby, EterVelocityLobby). Tapé par le joueur,
 * /lobby n'arrive jamais ici : le proxy le traite avant.
 */
public class LobbyCommand implements CommandExecutor {

    public static final String CHANNEL = "eter:lobby";

    private final JavaPlugin plugin;
    private final Messages messages;

    public LobbyCommand(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
        } else if (!player.getListeningPluginChannels().contains(CHANNEL)) {
            messages.send(player, "lobby.unavailable"); // proxy sans EterVelocityLobby 1.2.5+
        } else {
            player.sendPluginMessage(plugin, CHANNEL, new byte[0]);
        }
        return true;
    }
}
