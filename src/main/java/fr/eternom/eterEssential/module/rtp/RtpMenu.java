package fr.eternom.eterEssential.module.rtp;

import fr.eternom.eterEssential.module.info.TimeFormat;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu /rtp, 3 lignes :
 * <pre>
 *  ▣ ▣ ▢ ▢ ☺ ▢ ▢ ▣ ▣     ☺ = joueur (délai restant, en direct)
 *  ▣ · ◆ · ◆ · ◆ · ▣     ◆ = mondes de ce serveur (config.yml > rtp.worlds), jusqu'à 4
 *  ▣ ▣ ▢ ▢ ▢ ▢ ▢ ▣ ▣
 * </pre>
 */
class RtpMenu implements Menu {

    private static final int INFO = 4;
    private static final Set<Integer> ACCENT_FRAME = Set.of(0, 1, 7, 8, 9, 17, 18, 19, 25, 26);
    /** Emplacements des mondes selon leur nombre (1 à 4), centrés sur la ligne du milieu. */
    private static final List<List<Integer>> LAYOUTS = List.of(List.of(13), List.of(11, 15), List.of(11, 13, 15),
            List.of(10, 12, 14, 16));

    private final RtpService service;
    private final Messages messages;
    private final Player viewer;
    private final long cooldownUntil;
    private final Inventory inventory;
    private final Map<Integer, RtpWorld> worldAtSlot = new HashMap<>();
    private final BukkitTask refresher;

    RtpMenu(RtpService service, Messages messages, Player viewer, long cooldownSeconds) {
        this.service = service;
        this.messages = messages;
        this.viewer = viewer;
        this.cooldownUntil = System.currentTimeMillis() + cooldownSeconds * 1000;
        this.inventory = Bukkit.createInventory(this, 27, text("rtp.menu.title"));
        render();
        // Le délai change chaque seconde : seul l'item du joueur est redessiné
        refresher = Bukkit.getScheduler().runTaskTimer(service.plugin(), this::refreshInfo, 20, 20);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        RtpWorld world = worldAtSlot.get(slot);
        if (world == null) {
            return;
        }
        if (remainingSeconds() > 0) {
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        Sounds.click(player);
        player.closeInventory();
        service.start(player, world);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render() {
        ItemStack accent = Items.pane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack neutral = Items.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot / 9 != 1 || slot % 9 == 0 || slot % 9 == 8) {
                inventory.setItem(slot, ACCENT_FRAME.contains(slot) ? accent : neutral);
            }
        }
        List<RtpWorld> worlds = service.worlds();
        List<Integer> slots = LAYOUTS.get(Math.min(worlds.size(), LAYOUTS.size()) - 1);
        for (int i = 0; i < slots.size(); i++) {
            RtpWorld world = worlds.get(i);
            worldAtSlot.put(slots.get(i), world);
            inventory.setItem(slots.get(i), Items.item(world.icon(), text("rtp.menu.world", "world", worldName(world)), List.of(
                    text("rtp.menu.radius", "min", String.valueOf(world.minRadius()), "max", String.valueOf(world.maxRadius())),
                    Component.empty(),
                    text("rtp.menu.click"))));
        }
        refreshInfo();
    }

    private void refreshInfo() {
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) != this && refresher != null) {
            refresher.cancel(); // menu fermé
            return;
        }
        long remaining = remainingSeconds();
        List<Component> lore = new ArrayList<>();
        lore.add(remaining > 0
                ? text("rtp.menu.cooldown", "time", TimeFormat.format(messages, viewer, remaining))
                : text("rtp.menu.ready"));
        inventory.setItem(INFO, Items.head(viewer.getPlayerProfile(), text("rtp.menu.player", "player", viewer.getName()), lore));
    }

    /** Nom du monde dans la langue du joueur (rtp.worlds.<monde>), sinon son nom technique. */
    private String worldName(RtpWorld world) {
        String raw = messages.raw(viewer, "rtp.worlds." + world.world());
        return raw == null ? world.world() : messages.plain(viewer, "rtp.worlds." + world.world());
    }

    private long remainingSeconds() {
        return Math.max(0, (cooldownUntil - System.currentTimeMillis() + 999) / 1000);
    }

    private Component text(String key, String... placeholders) {
        return messages.get(viewer, key, placeholders);
    }
}
