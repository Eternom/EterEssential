package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterClan.api.ClanApi;
import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterMarket.api.MarketApi;
import fr.eternom.eterReward.api.RewardApi;
import fr.eternom.eterSuccess.api.SuccessApi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les infos de la tête du menu, lues par l'API des autres plugins Eter. Chaque plugin facultatif est lu dans sa propre
 * classe, chargée seulement s'il tourne ici (sinon NoClassDefFoundError). Une info absente (plugin pas sur ce serveur,
 * erreur) n'est simplement pas affichée.
 */
record ProfileInfo(String balance, String job, String clan, boolean clans, int passTier, int streak, int points, int rank) {

    private static final int NONE = -1;

    /** Bloquant (base) : hors du thread principal. */
    static ProfileInfo read(UUID player) {
        String balance = EconomyApi.get().map(economy -> economy.format(economy.balance(player))).orElse(null);
        String job = enabled("EterMarket") ? Market.job(player) : null;
        boolean clans = enabled("EterClan");
        String clan = clans ? Clans.clan(player) : null;
        int passTier = enabled("EterReward") ? Rewards.passTier(player) : NONE;
        int streak = enabled("EterReward") ? Rewards.streak(player) : NONE;
        int points = enabled("EterSuccess") ? Success.points(player) : NONE;
        int rank = points > 0 ? Success.rank(player) : NONE;
        return new ProfileInfo(balance, job, clan, clans, passTier, streak, points, rank);
    }

    /** Les lignes à afficher, dans la langue du joueur. Thread principal (le nom du métier vient d'EterMarket). */
    List<Component> lines(Messages messages, Player viewer) {
        List<Component> lines = new ArrayList<>();
        if (balance != null) {
            lines.add(messages.get(viewer, "menu.profile.balance", "value", balance));
        }
        if (job != null) {
            lines.add(job.isEmpty() ? messages.get(viewer, "menu.profile.no-job")
                    : messages.get(viewer, "menu.profile.job", "value", Market.name(viewer, job)));
        }
        if (clans) {
            lines.add(clan == null ? messages.get(viewer, "menu.profile.no-clan") : messages.get(viewer, "menu.profile.clan", "value", clan));
        }
        if (passTier != NONE) {
            lines.add(messages.get(viewer, "menu.profile.pass", "value", String.valueOf(passTier)));
        }
        if (streak != NONE) {
            lines.add(messages.get(viewer, "menu.profile.daily", "value", String.valueOf(streak)));
        }
        if (points != NONE) {
            lines.add(rank > 0 ? messages.get(viewer, "menu.profile.success-rank", "value", String.valueOf(points), "rank", String.valueOf(rank))
                    : messages.get(viewer, "menu.profile.success", "value", String.valueOf(points)));
        }
        return lines;
    }

    private static boolean enabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    private static final class Market {
        /** "" : pas de métier ; null : rien à lire. */
        static String job(UUID player) {
            return MarketApi.get().map(api -> api.job(player).orElse("")).orElse(null);
        }

        static String name(Player viewer, String job) {
            return MarketApi.get().map(api -> api.jobName(viewer, job)).orElse(job);
        }
    }

    private static final class Clans {
        static String clan(UUID player) {
            return ClanApi.get().flatMap(api -> api.clanOf(player)).map(clan -> "[" + clan.tag() + "] " + clan.name()).orElse(null);
        }
    }

    private static final class Rewards {
        static int passTier(UUID player) {
            return RewardApi.get().map(api -> api.passTier(player)).orElse(NONE);
        }

        static int streak(UUID player) {
            return RewardApi.get().map(api -> api.streak(player)).orElse(NONE);
        }
    }

    private static final class Success {
        static int points(UUID player) {
            return SuccessApi.get().map(api -> api.points(player)).orElse(NONE);
        }

        static int rank(UUID player) {
            return SuccessApi.get().map(api -> api.rank(player)).orElse(NONE);
        }
    }
}
