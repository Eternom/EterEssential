package fr.eternom.eterEssential.module.network;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import fr.eternom.eterLib.module.vanish.Vanish;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Retrouver un joueur par son pseudo : d'abord sur ce serveur, puis sur le réseau (eter_players).
 * Les réponses arrivent sur le thread principal ; une erreur de base prévient le demandeur.
 */
public class PlayerLookup {

    /** Joueur connecté quelque part sur le réseau. server : son nom dans le proxy. */
    public record OnlinePlayer(UUID uuid, String name, String server) {
    }

    private final JavaPlugin plugin;
    private final PlayerDirectory directory;
    private final Messages messages;
    private final String serverName;

    public PlayerLookup(JavaPlugin plugin, PlayerDirectory directory, Messages messages, String serverName) {
        this.plugin = plugin;
        this.directory = directory;
        this.messages = messages;
        this.serverName = serverName;
    }

    /**
     * Joueur connecté, sur ce serveur ou sur un autre. Un invisible (vanish du staff) que asker ne peut pas voir est
     * « hors ligne ».
     */
    public void findOnline(Player asker, String name, Consumer<Optional<OnlinePlayer>> then) {
        Vanish vanish = EterLib.get().getVanish();
        Player local = Bukkit.getPlayerExact(name);
        if (local != null) {
            if (!vanish.canSee(asker, local.getUniqueId())) {
                then.accept(Optional.empty());
                return;
            }
            then.accept(Optional.of(new OnlinePlayer(local.getUniqueId(), local.getName(), serverName)));
            return;
        }
        Tasks.async(plugin, asker, () -> directory.find(name).filter(NetworkPlayer::isOnline)
                        .filter(found -> vanish.canSee(asker, found.uuid()))
                        .map(found -> new OnlinePlayer(found.uuid(), found.name(), found.server())),
                then, () -> messages.send(asker, "error.generic"));
    }

    /** N'importe quel joueur déjà venu sur le réseau, même hors ligne (/seen, /pay) ; un invisible est montré hors ligne. */
    public void findAny(Player asker, String name, Consumer<Optional<NetworkPlayer>> then) {
        Vanish vanish = EterLib.get().getVanish();
        Tasks.async(plugin, asker, () -> directory.find(name).map(found -> found.isOnline() && !vanish.canSee(asker, found.uuid())
                ? new NetworkPlayer(found.uuid(), found.name(), found.locale(), null, found.firstSeen(), found.lastSeen()) : found), then, () -> messages.send(asker, "error.generic"));
    }

    /** Bloquant : serveur actuel d'un joueur, vide s'il est hors ligne. */
    public Optional<String> serverOf(UUID player) {
        return Bukkit.getPlayer(player) != null ? Optional.of(serverName) : directory.getServer(player);
    }

    public String serverName() {
        return serverName;
    }
}
