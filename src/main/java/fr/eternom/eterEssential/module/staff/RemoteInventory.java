package fr.eternom.eterEssential.module.staff;

import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterSync.api.SyncApi;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /invsee d'un joueur ailleurs ou hors ligne : sa dernière sauvegarde (API d'EterSync), en lecture seule. Classe à
 * part : chargée seulement si EterSync tourne (sinon, ses classes n'existent pas sur le serveur).
 */
final class RemoteInventory {

    private RemoteInventory() {
    }

    static void open(JavaPlugin plugin, PlayerLookup lookup, Messages messages, Player staff, String name) {
        SyncApi sync = SyncApi.get().orElse(null);
        if (sync == null) {
            messages.send(staff, "player.not-here", "player", name);
            return;
        }
        lookup.findAny(staff, name, found -> found.ifPresentOrElse(target -> Tasks.async(plugin, staff,
                        () -> sync.lastInventory(target.uuid()),
                        contents -> contents.ifPresentOrElse(
                                items -> staff.openInventory(new SnapshotView(messages.get(staff, "invsee.snapshot", "player", target.name()),
                                        items).getInventory()),
                                () -> messages.send(staff, "invsee.no-snapshot", "player", target.name())),
                        () -> messages.send(staff, "error.generic")),
                () -> messages.send(staff, "player.unknown", "player", name)));
    }
}
