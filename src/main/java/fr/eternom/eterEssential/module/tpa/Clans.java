package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterClan.api.ClanApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ce que le tpa demande à EterClan (jamais à ses tables) : même clan, membres, terrain. EterClan est facultatif : sans
 * lui, personne n'est du même clan et rien n'est un terrain (ses classes ne sont chargées que s'il tourne : Api).
 */
final class Clans {

    private Clans() {
    }

    private static boolean present() {
        return Bukkit.getPluginManager().isPluginEnabled("EterClan");
    }

    /** Bloquant (base). */
    static boolean same(UUID first, UUID second) {
        return present() && Api.same(first, second);
    }

    /** Bloquant (base) : les membres de son clan (lui compris), vide sans clan. */
    static List<UUID> members(UUID player) {
        return present() ? Api.members(player) : List.of();
    }

    /** Thread principal : ce bloc est dans le terrain d'un clan (ce serveur). */
    static boolean isClaimed(Location location) {
        return present() && Api.isClaimed(location);
    }

    private static final class Api {

        static boolean same(UUID first, UUID second) {
            return ClanApi.get().map(api -> {
                Long a = api.clanOf(first).map(ClanApi.ClanInfo::id).orElse(null);
                return a != null && Objects.equals(a, api.clanOf(second).map(ClanApi.ClanInfo::id).orElse(null));
            }).orElse(false);
        }

        static List<UUID> members(UUID player) {
            return ClanApi.get().flatMap(api -> api.clanOf(player)).map(ClanApi.ClanInfo::members).orElse(List.of());
        }

        static boolean isClaimed(Location location) {
            return ClanApi.get().map(api -> api.isClaimed(location)).orElse(false);
        }
    }
}
