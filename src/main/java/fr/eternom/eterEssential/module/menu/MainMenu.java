package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /menu : le menu central du joueur, une porte vers tout le reste, avec des sous-menus (Progression, Voyager, Social,
 * Outils...). Un bouton lance une commande d'un autre plugin, ou ouvre un sous-menu. Il n'est montré que s'il sert ici :
 * commande présente sur ce serveur et permise au joueur ; sous-menu avec au moins un bouton montré. Boutons réglés dans
 * config.yml > menu, textes dans lang/ (menu.button.<id>).
 */
public class MainMenu implements Menu {

    /** Un bouton : sa case, son icône, et soit une commande (sans le /), soit un sous-menu. */
    public record Button(String id, int slot, Material icon, String command, String submenu) {
    }

    private static final int HEAD = 4;
    private static final int BACK = 49;

    private final Messages messages;
    private final Map<String, List<Button>> pages;
    private final String page;
    private final Inventory inventory;
    private final Map<Integer, Button> shown = new HashMap<>();

    /** page : "main" pour le menu central, sinon l'identifiant du sous-menu. */
    public MainMenu(Messages messages, Player viewer, Map<String, List<Button>> pages, String page) {
        this.messages = messages;
        this.pages = pages;
        this.page = page;
        boolean main = page.equals(MenuCommand.MAIN);
        this.inventory = Bukkit.createInventory(this, 54, main ? messages.get(viewer, "menu.title")
                : messages.get(viewer, "menu.sub-title", "menu", messages.plain(viewer, "menu.button." + page + ".name")));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        if (main) {
            inventory.setItem(HEAD, Items.head(viewer.getPlayerProfile(), messages.get(viewer, "menu.player", "player", viewer.getName()),
                    List.of(messages.get(viewer, "menu.player-lore"))));
        }
        for (Button button : pages.getOrDefault(page, List.of())) {
            if (button.slot() < 0 || button.slot() >= 54 || !visible(viewer, button)) {
                continue;
            }
            List<Component> lore = new ArrayList<>();
            if (messages.raw(viewer, "menu.button." + button.id() + ".lore") != null) {
                lore.add(messages.get(viewer, "menu.button." + button.id() + ".lore"));
            }
            lore.add(Component.empty());
            lore.add(button.submenu() != null ? messages.get(viewer, "menu.open-sub")
                    : messages.get(viewer, "menu.open", "command", "/" + button.command()));
            inventory.setItem(button.slot(), Items.item(button.icon(), messages.get(viewer, "menu.button." + button.id() + ".name"), lore));
            shown.put(button.slot(), button);
        }
        inventory.setItem(BACK, main ? Items.item(Material.BARRIER, messages.get(viewer, "menu.close"), List.of())
                : Items.item(Material.OAK_DOOR, messages.get(viewer, "menu.back"), List.of()));
    }

    /** Commande présente ici et permise ; sous-menu avec au moins un bouton visible. */
    private boolean visible(Player viewer, Button button) {
        if (button.submenu() != null) {
            return !button.submenu().equals(page) && pages.getOrDefault(button.submenu(), List.of()).stream()
                    .anyMatch(child -> child.submenu() == null && visible(viewer, child));
        }
        Command command = Bukkit.getCommandMap().getCommand(button.command().split(" ")[0]);
        return command != null && command.testPermissionSilent(viewer);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            Sounds.click(player);
            if (page.equals(MenuCommand.MAIN)) {
                player.closeInventory();
            } else {
                player.openInventory(new MainMenu(messages, player, pages, MenuCommand.MAIN).getInventory());
            }
            return;
        }
        Button button = shown.get(slot);
        if (button == null) {
            return;
        }
        Sounds.click(player);
        if (button.submenu() != null) {
            player.openInventory(new MainMenu(messages, player, pages, button.submenu()).getInventory());
        } else {
            player.closeInventory();
            player.performCommand(button.command());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
