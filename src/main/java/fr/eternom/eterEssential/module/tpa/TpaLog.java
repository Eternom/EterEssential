package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le journal des tpa (eteressential_tpa_log) : chaque départ réel, qui est allé vers qui, quand, depuis quel serveur.
 * Pour le staff (/tpalog, une enquête de vol ou de piège). Gardé tpa.log-days jours. Bloquant.
 */
public class TpaLog {

    /** Une téléportation : traveller est allé vers destination ; here : à la demande de destination (/tpahere). */
    public record Entry(String traveller, String destination, boolean here, String server, long createdAt) {
    }

    private static final String TABLE = "tpa_log";

    private final Database database;
    private final String table;

    public TpaLog(Database database) {
        this.database = database;
        database.createTable(TABLE,
                Column.of("id", Column.Type.LONG).autoIncrement(),
                Column.of("traveller", Column.Type.UUID).notNull(),
                Column.of("traveller_name", Column.Type.STRING).length(16).notNull(),
                Column.of("destination", Column.Type.UUID).notNull(),
                Column.of("destination_name", Column.Type.STRING).length(16).notNull(),
                Column.of("here", Column.Type.BOOLEAN).notNull(),
                Column.of("server", Column.Type.STRING).length(64).notNull(),
                Column.of("created_at", Column.Type.LONG).notNull());
        this.table = database.table(TABLE);
        database.execute("CREATE INDEX IF NOT EXISTS idx_traveller ON " + table + " (traveller)");
        database.execute("CREATE INDEX IF NOT EXISTS idx_destination ON " + table + " (destination)");
    }

    public void add(UUID traveller, String travellerName, UUID destination, String destinationName, boolean here, String server) {
        database.insert(TABLE, Map.of("traveller", traveller, "traveller_name", travellerName, "destination", destination,
                "destination_name", destinationName, "here", here, "server", server, "created_at", System.currentTimeMillis()));
    }

    /** Ses dernières téléportations, dans un sens ou dans l'autre. */
    public List<Entry> of(UUID player, int limit) {
        return database.query("SELECT * FROM " + table + " WHERE traveller = ? OR destination = ? ORDER BY created_at DESC LIMIT ?",
                        player, player, limit).stream()
                .map(row -> new Entry(row.getString("traveller_name"), row.getString("destination_name"), row.getBoolean("here"),
                        row.getString("server"), row.getLong("created_at"))).toList();
    }

    public void purge(int keepDays) {
        database.execute("DELETE FROM " + table + " WHERE created_at < ?", System.currentTimeMillis() - keepDays * 86_400_000L);
    }
}
