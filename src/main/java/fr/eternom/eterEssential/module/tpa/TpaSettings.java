package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.util.Map;
import java.util.UUID;

/**
 * Joueurs qui refusent toutes les demandes de tpa (/tptoggle), table eteressential_players : le choix vaut sur tout
 * le réseau. Lu à chaque demande (le destinataire peut être sur un autre serveur). Appels bloquants.
 */
public class TpaSettings {

    private static final String TABLE = "players";

    private final Database database;

    public TpaSettings(Database database) {
        this.database = database;
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("tpa_disabled", Column.Type.BOOLEAN).notNull());
    }

    public boolean isDisabled(UUID player) {
        return database.getFirst(TABLE, Map.of("uuid", player)).map(row -> row.getBoolean("tpa_disabled")).orElse(false);
    }

    /** @return true si les demandes sont maintenant refusées */
    public boolean toggle(UUID player) {
        boolean disabled = !isDisabled(player);
        database.set(TABLE, Map.of("uuid", player, "tpa_disabled", disabled), "uuid");
        return disabled;
    }
}
