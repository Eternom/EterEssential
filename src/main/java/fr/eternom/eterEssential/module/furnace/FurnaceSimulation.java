package fr.eternom.eterEssential.module.furnace;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * La cuisson d'un four, recopiée du jeu : le combustible brûle, l'objet cuit au rythme de sa recette (10 s pour la
 * plupart), la cuisson recule quand le feu s'éteint. Aucun bloc derrière : c'est ce code qui fait cuire, pendant que le
 * menu est ouvert (tick par tick) et pour le temps passé fermé (d'un coup, à l'ouverture).
 */
public class FurnaceSimulation {

    private final List<FurnaceRecipe> recipes = new ArrayList<>();

    /** Les recettes du four (pas du haut fourneau ni du fumoir), lues une fois : celles des datapacks comprises. */
    public FurnaceSimulation() {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            if (iterator.next() instanceof FurnaceRecipe recipe) {
                recipes.add(recipe);
            }
        }
    }

    FurnaceRecipe recipe(ItemStack input) {
        if (isEmpty(input)) {
            return null;
        }
        for (FurnaceRecipe recipe : recipes) {
            if (recipe.getInputChoice().test(input)) {
                return recipe;
            }
        }
        return null;
    }

    static int burnTicks(ItemStack fuel) {
        return isEmpty(fuel) || !fuel.getType().isFuel() ? 0 : fuel.getType().asItemType().getBurnDuration();
    }

    /** Fait avancer la cuisson de ticks. Par bonds quand rien ne change, pour rattraper des heures sans boucler à vide. */
    void advance(FurnaceState state, long ticks) {
        while (ticks > 0) {
            FurnaceRecipe recipe = recipe(state.input);
            boolean canCook = recipe != null && fits(state.output, recipe.getResult());
            if (state.lit <= 0 && canCook && burnTicks(state.fuel) > 0) {
                state.litTotal = burnTicks(state.fuel);
                state.lit = state.litTotal;
                consumeFuel(state);
            }
            if (state.lit <= 0) {
                // Feu éteint : la cuisson recule de 2 par tick, puis plus rien ne bouge
                state.cook = (int) Math.max(0, state.cook - 2 * ticks);
                return;
            }
            if (!canCook) {
                // Le feu brûle pour rien (rien à cuire, ou sortie pleine) : il s'éteint
                long step = Math.min(ticks, state.lit);
                state.lit -= (int) step;
                state.cook = 0;
                ticks -= step;
                continue;
            }
            int total = recipe.getCookingTime();
            long step = Math.min(ticks, Math.min(state.lit, total - state.cook));
            state.lit -= (int) step;
            state.cook += (int) step;
            ticks -= step;
            if (state.cook >= total) {
                state.cook = 0;
                state.input = shrink(state.input);
                state.output = isEmpty(state.output) ? recipe.getResult().clone() : grow(state.output, recipe.getResult().getAmount());
                state.xp += recipe.getExperience();
            }
        }
    }

    private static void consumeFuel(FurnaceState state) {
        // Un seau de lave laisse un seau vide, comme dans le jeu
        if (state.fuel.getType() == Material.LAVA_BUCKET) {
            state.fuel = new ItemStack(Material.BUCKET);
            return;
        }
        state.fuel = shrink(state.fuel);
    }

    static boolean fits(ItemStack output, ItemStack result) {
        return isEmpty(output) || (output.isSimilar(result) && output.getAmount() + result.getAmount() <= output.getMaxStackSize());
    }

    private static ItemStack shrink(ItemStack item) {
        if (item.getAmount() <= 1) {
            return null;
        }
        ItemStack less = item.clone();
        less.setAmount(item.getAmount() - 1);
        return less;
    }

    private static ItemStack grow(ItemStack item, int amount) {
        ItemStack more = item.clone();
        more.setAmount(item.getAmount() + amount);
        return more;
    }

    static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
