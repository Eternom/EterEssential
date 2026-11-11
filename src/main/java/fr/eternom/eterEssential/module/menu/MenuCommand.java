package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterEssential.module.menu.MainMenu.Button;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /menu (/profil) : ouvre le menu central. */
public class MenuCommand implements TabExecutor {

    /** Identifiant de la page principale (config.yml > menu.main). */
    static final String MAIN = "main";

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Map<String, List<Button>> pages;

    public MenuCommand(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.pages = load(plugin);
    }

    JavaPlugin plugin() {
        return plugin;
    }

    Messages messages() {
        return messages;
    }

    Map<String, List<Button>> pages() {
        return pages;
    }

    /** Ouvre une page (MAIN ou un sous-menu). */
    void open(Player player, String page) {
        player.openInventory(new MainMenu(this, player, pages.containsKey(page) ? page : MAIN).getInventory());
    }

    /** config.yml > menu.main (page principale) et menu.submenus.<id> (sous-menus) : id -> slot, icon, command ou menu. */
    private static Map<String, List<Button>> load(JavaPlugin plugin) {
        Map<String, List<Button>> pages = new LinkedHashMap<>();
        pages.put(MAIN, buttons(plugin, plugin.getConfig().getConfigurationSection("menu.main"), "menu.main"));
        ConfigurationSection submenus = plugin.getConfig().getConfigurationSection("menu.submenus");
        if (submenus != null) {
            for (String id : keys(plugin, submenus, "menu.submenus")) {
                pages.put(id, buttons(plugin, plugin.getConfig().getConfigurationSection("menu.submenus." + id), "menu.submenus." + id));
            }
        }
        return Map.copyOf(pages);
    }

    /**
     * Les clés de la config du serveur, plus celles ajoutées depuis dans la config par défaut du plugin : un nouveau bouton
     * apparaît aussi sur un serveur qui garde son ancien config.yml (pour en cacher un : slot: -1). Les valeurs absentes
     * du fichier sont lues dans les défauts par Bukkit.
     */
    private static java.util.Set<String> keys(JavaPlugin plugin, ConfigurationSection section, String path) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(section.getKeys(false));
        org.bukkit.configuration.Configuration defaults = plugin.getConfig().getDefaults();
        ConfigurationSection fallback = defaults == null ? null : defaults.getConfigurationSection(path);
        if (fallback != null) {
            keys.addAll(fallback.getKeys(false));
        }
        return keys;
    }

    private static List<Button> buttons(JavaPlugin plugin, ConfigurationSection section, String path) {
        List<Button> buttons = new ArrayList<>();
        if (section == null) {
            return buttons;
        }
        for (String id : keys(plugin, section, path)) {
            String command = section.getString(id + ".command");
            String submenu = section.getString(id + ".menu");
            Material icon = Material.matchMaterial(section.getString(id + ".icon", "PAPER"));
            if ((command == null || command.isBlank()) && (submenu == null || submenu.isBlank())) {
                plugin.getLogger().warning(path + "." + id + " : ni command ni menu, bouton ignoré");
                continue;
            }
            buttons.add(new Button(id, section.getInt(id + ".slot", -1), icon == null || !icon.isItem() ? Material.PAPER : icon,
                    command == null ? null : command.startsWith("/") ? command.substring(1) : command,
                    submenu == null || submenu.isBlank() ? null : submenu, section.getBoolean(id + ".hide-on-lobby")));
        }
        return List.copyOf(buttons);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        open(player, args.length > 0 ? args[0] : MAIN);
        return true;
    }

    /** Choisir un joueur pour une commande avec {player} (ex : /tpa sans pseudo) ; Retour ferme le menu. */
    public void pickPlayer(Player player, String command) {
        MenuInputs.run(this, player, command, player::closeInventory);
    }

    /** Les sous-menus. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? pages.keySet().stream().filter(page -> !page.equals(MAIN) && page.startsWith(args[0])).sorted().toList()
                : List.of();
    }
}
