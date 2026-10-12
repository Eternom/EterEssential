package fr.eternom.eterEssential.module.furnace;

import org.bukkit.inventory.ItemStack;

/**
 * Ce que contient le four portatif d'un joueur : les trois cases et où en est la cuisson, comme un vrai four.
 * Les temps sont en ticks (20 par seconde).
 */
public class FurnaceState {

    ItemStack input;
    ItemStack fuel;
    ItemStack output;
    /** Avancée de la cuisson de l'objet en cours. */
    int cook;
    /** Reste du combustible en train de brûler, et sa durée totale (pour la flamme du menu). */
    int lit;
    int litTotal;
    /** Expérience gagnée et pas encore donnée (la partie entière est donnée à la fermeture). */
    float xp;
    /** Dernière fois que l'état a été mis à jour : la cuisson continue pendant que le four est fermé. */
    long savedAt;

    /** Copie à enregistrer en tâche de fond, pendant que le serveur continue de faire cuire l'original. */
    FurnaceState copy() {
        FurnaceState copy = new FurnaceState();
        copy.input = input == null ? null : input.clone();
        copy.fuel = fuel == null ? null : fuel.clone();
        copy.output = output == null ? null : output.clone();
        copy.cook = cook;
        copy.lit = lit;
        copy.litTotal = litTotal;
        copy.xp = xp;
        copy.savedAt = savedAt;
        return copy;
    }

    static FurnaceState empty() {
        FurnaceState state = new FurnaceState();
        state.savedAt = System.currentTimeMillis();
        return state;
    }
}
