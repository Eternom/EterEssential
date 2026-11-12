package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * /menu : le menu central du joueur, une porte vers tout le reste. Page principale : les catégories (sous-menus) en
 * haut, puis quelques raccourcis vers ce qui sert le plus souvent. Un bouton lance une commande d'un autre plugin, ou ouvre un
 * sous-menu. Une commande avec {player} fait d'abord choisir un joueur, avec {amount} ou {text} demande la valeur
 * (Dialog) : /pay, /tpa, /msg sans rien taper. Un bouton n'est montré que s'il sert ici : commande présente sur ce
 * serveur et permise au joueur ; sous-menu avec au moins un bouton montré. Boutons réglés dans config.yml > menu,
 * textes dans lang/ (menu.button.<id>).
 */
public class MainMenu implements Menu {

    /** Un bouton : sa case, son icône, et soit une commande (sans le /), soit un sous-menu ; hideOnLobby : caché sur un lobby. */
    public record Button(String id, int slot, Material icon, String command, String submenu, boolean hideOnLobby) {
    }

    private static final int HEAD = 4;
    /** 5 lignes : peu de boutons, faciles à trouver. */
    private static final int SIZE = 45;
    private static final int BACK = 40;

    private final MenuCommand menus;
    private final Messages messages;
    private final String page;
    private final Inventory inventory;
    private final Map<Integer, Button> shown = new HashMap<>();
    /** Joueur visé (/menu player &lt;pseudo&gt;) : remplace {player} dans les commandes ; null : choisi au clic. */
    private final String target;
    /** Retour d'une page « joueur » : vers le menu d'où il vient (liste d'amis, profil du hub...), à la même page. */
    private final BackButton back = fr.eternom.eterLib.EterLib.get().backButton("");

    /** page : "main" pour le menu central, sinon l'identifiant du sous-menu. */
    MainMenu(MenuCommand menus, Player viewer, String page) {
        this(menus, viewer, page, null);
    }

    MainMenu(MenuCommand menus, Player viewer, String page, String target) {
        this.menus = menus;
        this.messages = menus.messages();
        this.page = page;
        this.target = target;
        boolean main = page.equals(MenuCommand.MAIN);
        this.inventory = Bukkit.createInventory(this, SIZE, main ? messages.get(viewer, "menu.title")
                : target != null ? messages.get(viewer, "menu.player-title", "player", target)
                : messages.get(viewer, "menu.sub-title", "menu", messages.plain(viewer, "menu.button." + page + ".name")));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        Player online = target == null ? null : Bukkit.getPlayerExact(target);
        if (online != null) {
            inventory.setItem(HEAD, Items.head(online.getPlayerProfile(), messages.get(viewer, "menu.player", "player", online.getName()),
                    List.of()));
        }
        if (main) {
            inventory.setItem(HEAD, profile(viewer, List.of()));
            Tasks.async(menus.plugin(), viewer, () -> ProfileInfo.read(viewer.getUniqueId()), read -> {
                if (viewer.getOpenInventory().getTopInventory().getHolder() == this) {
                    ProfileInfo info = read.withQuestsReady(viewer);
                    inventory.setItem(HEAD, profile(viewer, info.lines(messages, viewer)));
                    highlight(viewer, info);
                }
            }, () -> {
            });
        }
        for (Map.Entry<Integer, Button> placed : layout(viewer).entrySet()) {
            Button button = placed.getValue();
            List<Component> lore = new ArrayList<>();
            if (messages.raw(viewer, "menu.button." + button.id() + ".lore") != null) {
                lore.add(messages.get(viewer, "menu.button." + button.id() + ".lore"));
            }
            lore.add(Component.empty());
            String command = command(button);
            lore.add(button.submenu() != null || MenuInputs.asks(command) ? messages.get(viewer, "menu.open-sub")
                    : messages.get(viewer, "menu.open", "command", "/" + command));
            inventory.setItem(placed.getKey(), Items.item(button.icon(), messages.get(viewer, "menu.button." + button.id() + ".name"), lore));
            shown.put(placed.getKey(), button);
        }
        inventory.setItem(BACK, main ? Items.item(Material.BARRIER, messages.get(viewer, "menu.close"), List.of())
                : target != null ? back.item(viewer)
                : Items.item(Material.OAK_DOOR, messages.get(viewer, "menu.back"), List.of()));
    }

    /**
     * Pastilles : les boutons de ce qui attend le joueur brillent, avec une ligne en tête de lore (récompense du jour,
     * boîte des quêtes ; Progression, qui les contient).
     */
    private void highlight(Player viewer, ProfileInfo info) {
        for (Map.Entry<Integer, Button> entry : shown.entrySet()) {
            Button button = entry.getValue();
            String command = button.command() == null ? "" : button.command().split(" ")[0];
            boolean progression = "progression".equals(button.submenu()) || "rewards".equals(button.submenu());
            boolean waiting = info.dailyReady() && (command.equals("daily") || progression)
                    || (info.questBox() > 0 || info.questsReady() > 0) && (command.equals("quests") || progression)
                    || (info.passRewards() > 0 || info.parcels() > 0) && (command.equals("pass") || progression)
                    || info.parcels() > 0 && button.command() != null && button.command().equals("pass box")
                    || info.firstStep() != null && (command.equals("achievements") || progression);
            ItemStack item = inventory.getItem(entry.getKey());
            if (!waiting || item == null) {
                continue;
            }
            item.editMeta(meta -> {
                meta.setEnchantmentGlintOverride(true);
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(0, messages.get(viewer, "menu.todo.badge"));
                meta.lore(lore);
            });
            inventory.setItem(entry.getKey(), item);
        }
    }

    /** La tête du joueur : son nom, puis ses infos (lues en tâche de fond). */
    private ItemStack profile(Player viewer, List<Component> info) {
        List<Component> lore = new ArrayList<>(info);
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(messages.get(viewer, "menu.player-lore"));
        return Items.head(viewer.getPlayerProfile(), messages.get(viewer, "menu.player", "player", viewer.getName()), lore);
    }

    /**
     * Case -> bouton montré. Une ligne garde les cases de la config si tous ses boutons sont montrés ; sinon (commande
     * absente de ce serveur, pas la permission), ceux qui restent sont recentrés, dans l'ordre : pas de trou.
     */
    private Map<Integer, Button> layout(Player viewer) {
        Map<Integer, List<Button>> rows = new TreeMap<>();
        for (Button button : menus.pages().getOrDefault(page, List.of())) {
            if (button.slot() >= 0 && button.slot() < SIZE) {
                rows.computeIfAbsent(button.slot() / 9, row -> new ArrayList<>()).add(button);
            }
        }
        Map<Integer, Button> layout = new LinkedHashMap<>();
        rows.forEach((row, buttons) -> {
            List<Button> kept = buttons.stream().filter(button -> visible(viewer, button))
                    .sorted(Comparator.comparingInt(Button::slot)).toList();
            if (kept.size() == buttons.size()) {
                kept.forEach(button -> layout.put(button.slot(), button));
                return;
            }
            for (int i = 0; i < kept.size() && i < 7; i++) {
                layout.put(row * 9 + column(i, Math.min(7, kept.size())), kept.get(i));
            }
        });
        return layout;
    }

    /**
     * Colonne (1 à 7) du i-ème de count boutons, symétrique autour de la colonne du milieu (4) : un nombre impair est
     * centré d'un bloc, un nombre pair laisse la colonne du milieu vide (4 boutons : 2, 3, _, 5, 6).
     */
    private static int column(int i, int count) {
        if (count % 2 == 1) {
            return 4 - count / 2 + i;
        }
        int half = count / 2;
        return i < half ? 4 - half + i : 5 + i - half;
    }

    /** Commande présente ici et permise ; sous-menu avec au moins un bouton visible. */
    private boolean visible(Player viewer, Button button) {
        if (button.hideOnLobby() && Bukkit.getPluginManager().getPlugin("EterHub") != null) {
            return false; // ex : « Lobby » sur un lobby, où « Spawn » prend sa place
        }
        if (button.submenu() != null) {
            return !button.submenu().equals(page) && menus.pages().getOrDefault(button.submenu(), List.of()).stream()
                    .anyMatch(child -> child.submenu() == null && visible(viewer, child));
        }
        Command command = Bukkit.getCommandMap().getCommand(button.command().split(" ")[0]);
        return command != null && command.testPermissionSilent(viewer);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            if (target != null) {
                back.click(player);
                return;
            }
            Sounds.click(player);
            if (page.equals(MenuCommand.MAIN)) {
                player.closeInventory();
            } else {
                menus.open(player, MenuCommand.MAIN);
            }
            return;
        }
        Button button = shown.get(slot);
        if (button == null) {
            return;
        }
        Sounds.click(player);
        if (button.submenu() != null) {
            menus.open(player, button.submenu());
        } else {
            // Annuler une saisie ramène sur cette page, pas hors du menu
            MenuInputs.run(menus, player, command(button), () -> menus.open(player, page, target));
        }
    }

    /** La commande du bouton, {player} déjà rempli quand le menu vise un joueur. */
    private String command(Button button) {
        return button.command() == null || target == null ? button.command() : button.command().replace("{player}", target);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
