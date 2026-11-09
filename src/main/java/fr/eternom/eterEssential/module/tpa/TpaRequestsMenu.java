package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterEssential.module.tpa.TpaRequests.Request;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les demandes de tpa reçues (/tpa list, ou /tpaccept avec plusieurs demandes) : la tête de chaque joueur, ce qu'il
 * demande, le temps restant. Clic gauche : accepter ; clic droit : refuser.
 */
class TpaRequestsMenu implements Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int CLOSE = 22;

    private final TpaService tpa;
    private final Inventory inventory;
    private final Map<Integer, Request> requestAt = new HashMap<>();

    TpaRequestsMenu(TpaService tpa, Messages messages, Player viewer, List<Request> requests) {
        this.tpa = tpa;
        this.inventory = Bukkit.createInventory(this, 27, messages.get(viewer, "tpa.menu.title", "count", String.valueOf(requests.size())));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        long now = System.currentTimeMillis();
        for (int i = 0; i < SLOTS.length && i < requests.size(); i++) {
            Request request = requests.get(i);
            requestAt.put(SLOTS[i], request);
            inventory.setItem(SLOTS[i], Items.head(Bukkit.createProfile(request.requester(), request.requesterName()),
                    messages.get(viewer, "tpa.menu.name", "player", request.requesterName()), List.of(
                            messages.get(viewer, request.here() ? "tpa.menu.wants-here" : "tpa.menu.wants"),
                            messages.get(viewer, "tpa.menu.time", "seconds", String.valueOf(request.secondsLeft(now)),
                                    "server", request.server() == null || request.server().isBlank() ? "?" : request.server()),
                            messages.get(viewer, "tpa.menu.click"))));
        }
        inventory.setItem(CLOSE, Items.item(Material.BARRIER, messages.get(viewer, "menu.close"), List.of()));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        Request request = requestAt.get(slot);
        if (request != null) {
            Sounds.click(player);
            player.closeInventory();
            if (click.isRightClick()) {
                tpa.deny(player, request.requesterName());
            } else {
                tpa.accept(player, request);
            }
        } else if (slot == CLOSE) {
            player.closeInventory();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
