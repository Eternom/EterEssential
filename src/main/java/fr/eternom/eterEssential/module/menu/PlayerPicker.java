package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterLib.EterLib;
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
 * Choisir un joueur connecté (ce serveur et les autres, sans les invisibles) pour une commande du menu ({player}) :
 * /tpa, /pay, /msg... sans taper son pseudo. 28 têtes par page.
 */
class PlayerPicker implements Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int PREVIOUS = 48;
    private static final int BACK = 49;
    private static final int NEXT = 50;

    private final MenuCommand menus;
    private final String command;
    private final Runnable back;
    private final int page;
    private final boolean hasNext;
    private final Inventory inventory;
    private final Map<Integer, String> names = new HashMap<>();

    PlayerPicker(MenuCommand menus, Player viewer, String command, Runnable back, int page) {
        this.menus = menus;
        this.command = command;
        this.back = back;
        this.page = page;
        Messages messages = menus.messages();
        List<String> online = EterLib.get().getOnlineNames().complete("").stream()
                .filter(name -> !name.equalsIgnoreCase(viewer.getName()))
                .toList();
        this.hasNext = (page + 1) * SLOTS.length < online.size();
        this.inventory = Bukkit.createInventory(this, 54, messages.get(viewer, "menu.picker.title",
                "command", "/" + command.split(" ")[0]));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        for (int i = 0; i < SLOTS.length && page * SLOTS.length + i < online.size(); i++) {
            String name = online.get(page * SLOTS.length + i);
            Player here = Bukkit.getPlayerExact(name);
            inventory.setItem(SLOTS[i], Items.head(here != null ? here.getPlayerProfile() : Bukkit.createProfile(name),
                    messages.get(viewer, "menu.picker.player", "player", name), List.of(messages.get(viewer, "menu.picker.pick"))));
            names.put(SLOTS[i], name);
        }
        if (online.isEmpty()) {
            inventory.setItem(22, Items.item(Material.BARRIER, messages.get(viewer, "menu.picker.nobody"), List.of()));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS, Items.item(Material.ARROW, messages.get(viewer, "menu.picker.previous"), List.of()));
        }
        if (hasNext) {
            inventory.setItem(NEXT, Items.item(Material.ARROW, messages.get(viewer, "menu.picker.next"), List.of()));
        }
        inventory.setItem(BACK, Items.item(Material.OAK_DOOR, messages.get(viewer, "menu.back"), List.of()));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            Sounds.click(player);
            back.run();
        } else if (slot == PREVIOUS && page > 0 || slot == NEXT && hasNext) {
            Sounds.click(player);
            player.openInventory(new PlayerPicker(menus, player, command, back, slot == NEXT ? page + 1 : page - 1).getInventory());
        } else if (names.containsKey(slot)) {
            Sounds.click(player);
            MenuInputs.withPlayer(menus, player, command, names.get(slot), back);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
