package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les listes de tpa d'un joueur (eteressential_tpa_lists), valables sur tout le réseau. Bloquant.
 * - IGNORE (/tpaignore) : ses demandes n'arrivent plus (il croit le joueur fermé aux demandes) ;
 * - AUTO (/tpaauto) : ses demandes sont acceptées toutes seules (amis), et il passe le mode « amis » de /tptoggle.
 */
public class TpaLists {

    public enum Kind { IGNORE, AUTO }

    /** Un joueur d'une liste. */
    public record Entry(UUID player, String name) {
    }

    private static final String TABLE = "tpa_lists";

    private final Database database;
    private final String table;

    public TpaLists(Database database) {
        this.database = database;
        database.createTable(TABLE,
                Column.of("owner", Column.Type.UUID).primaryKey(),
                Column.of("other", Column.Type.UUID).primaryKey(),
                Column.of("kind", Column.Type.STRING).length(8).primaryKey(),
                Column.of("name", Column.Type.STRING).length(16).notNull());
        this.table = database.table(TABLE);
    }

    public boolean contains(UUID owner, Kind kind, UUID other) {
        return database.getFirst(TABLE, Map.of("owner", owner, "other", other, "kind", kind.name())).isPresent();
    }

    /** Ajoute, ou retire s'il y était ; true s'il est maintenant dans la liste. */
    public boolean toggle(UUID owner, Kind kind, UUID other, String name) {
        if (contains(owner, kind, other)) {
            database.delete(TABLE, Map.of("owner", owner, "other", other, "kind", kind.name()));
            return false;
        }
        database.set(TABLE, Map.of("owner", owner, "other", other, "kind", kind.name(), "name", name), "owner", "other", "kind");
        return true;
    }

    public List<Entry> list(UUID owner, Kind kind) {
        return database.query("SELECT other, name FROM " + table + " WHERE owner = ? AND kind = ? ORDER BY name", owner, kind.name())
                .stream().map(row -> new Entry(row.getUUID("other"), row.getString("name"))).toList();
    }
}
