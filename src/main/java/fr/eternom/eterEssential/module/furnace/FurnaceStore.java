package fr.eternom.eterEssential.module.furnace;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le four portatif de chaque joueur, table eteressential_furnaces : le même sur tout le réseau. Appels bloquants.
 * Le contenu reste même si le joueur perd la permission : il le retrouve en la récupérant.
 */
public class FurnaceStore {

    private static final String TABLE = "furnaces";

    private final Database database;

    public FurnaceStore(Database database) {
        this.database = database;
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("input", Column.Type.BLOB),
                Column.of("fuel", Column.Type.BLOB),
                Column.of("output", Column.Type.BLOB),
                Column.of("cook", Column.Type.INT).notNull(),
                Column.of("lit", Column.Type.INT).notNull(),
                Column.of("lit_total", Column.Type.INT).notNull(),
                Column.of("xp", Column.Type.FLOAT).notNull(),
                Column.of("saved_at", Column.Type.LONG).notNull());
    }

    public Optional<FurnaceState> load(UUID player) {
        return database.getFirst(TABLE, Map.of("uuid", player)).map(FurnaceStore::read);
    }

    public void save(UUID player, FurnaceState state) {
        Map<String, Object> values = new HashMap<>();
        values.put("uuid", player);
        values.put("input", bytes(state.input));
        values.put("fuel", bytes(state.fuel));
        values.put("output", bytes(state.output));
        values.put("cook", state.cook);
        values.put("lit", state.lit);
        values.put("lit_total", state.litTotal);
        values.put("xp", state.xp);
        values.put("saved_at", state.savedAt);
        database.set(TABLE, values, "uuid");
    }

    private static FurnaceState read(Row row) {
        FurnaceState state = new FurnaceState();
        state.input = item(row.getBytes("input"));
        state.fuel = item(row.getBytes("fuel"));
        state.output = item(row.getBytes("output"));
        state.cook = row.getInt("cook");
        state.lit = row.getInt("lit");
        state.litTotal = row.getInt("lit_total");
        state.xp = row.getFloat("xp");
        state.savedAt = row.getLong("saved_at");
        return state;
    }

    private static byte[] bytes(ItemStack item) {
        return FurnaceSimulation.isEmpty(item) ? null : item.serializeAsBytes();
    }

    private static ItemStack item(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
    }
}
