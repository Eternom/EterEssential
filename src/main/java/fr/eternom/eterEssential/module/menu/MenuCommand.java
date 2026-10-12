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

    private final Messages messages;
    private final Map<String, List<Button>> pages;

    public MenuCommand(JavaPlugin plugin, Messages messages) {
        this.messages = messages;
        this.pages = load(plugin);
    }

    /** config.yml > menu.main (page principale) et menu.submenus.<id> (sous-menus) : id -> slot, icon, command ou menu. */
    private static Map<String, List<Button>> load(JavaPlugin plugin) {
        Map<String, List<Button>> pages = new LinkedHashMap<>();
        pages.put(MAIN, buttons(plugin, plugin.getConfig().getConfigurationSection("menu.main"), "menu.main"));
        ConfigurationSection submenus = plugin.getConfig().getConfigurationSection("menu.submenus");
        if (submenus != null) {
            for (String id : submenus.getKeys(false)) {
                pages.put(id, buttons(plugin, submenus.getConfigurationSection(id), "menu.submenus." + id));
            }
        }
        return Map.copyOf(pages);
    }

    private static List<Button> buttons(JavaPlugin plugin, ConfigurationSection section, String path) {
        List<Button> buttons = new ArrayList<>();
        if (section == null) {
            return buttons;
        }
        for (String id : section.getKeys(false)) {
            String command = section.getString(id + ".command");
            String submenu = section.getString(id + ".menu");
            Material icon = Material.matchMaterial(section.getString(id + ".icon", "PAPER"));
            if ((command == null || command.isBlank()) && (submenu == null || submenu.isBlank())) {
                plugin.getLogger().warning(path + "." + id + " : ni command ni menu, bouton ignoré");
                continue;
            }
            buttons.add(new Button(id, section.getInt(id + ".slot", -1), icon == null || !icon.isItem() ? Material.PAPER : icon,
                    command == null ? null : command.startsWith("/") ? command.substring(1) : command,
                    submenu == null || submenu.isBlank() ? null : submenu));
        }
        return List.copyOf(buttons);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        String page = args.length > 0 && pages.containsKey(args[0]) ? args[0] : MAIN;
        player.openInventory(new MainMenu(messages, player, pages, page).getInventory());
        return true;
    }

    /** Les sous-menus. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? pages.keySet().stream().filter(page -> !page.equals(MAIN) && page.startsWith(args[0])).sorted().toList()
                : List.of();
    }
}
