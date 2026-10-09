package fr.eternom.eterEssential.module.staff;

import fr.eternom.eterLib.helper.gui.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Inventaire d'un joueur absent d'ici, en LECTURE SEULE (sa dernière sauvegarde, lue par l'API d'EterSync) : rien ne
 * peut être pris (les clics des menus Eter sont annulés). 6 lignes : l'inventaire (4 lignes), puis armure et seconde main.
 */
class SnapshotView implements Menu {

    private final Inventory inventory;

    SnapshotView(Component title, ItemStack[] contents) {
        this.inventory = Bukkit.createInventory(this, 54, title);
        // contents : 0-8 barre, 9-35 sac, 36-39 armure (bottes -> casque), 40 seconde main
        for (int slot = 0; slot < 36 && slot < contents.length; slot++) {
            inventory.setItem(slot < 9 ? slot + 27 : slot - 9, contents[slot]);
        }
        for (int armor = 0; armor < 4 && 36 + armor < contents.length; armor++) {
            inventory.setItem(45 + 3 - armor, contents[36 + armor]);
        }
        if (contents.length > 40) {
            inventory.setItem(50, contents[40]);
        }
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        // lecture seule
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
