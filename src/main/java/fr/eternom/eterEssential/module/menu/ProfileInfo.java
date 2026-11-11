package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterClan.api.ClanApi;
import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterJob.api.JobApi;
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
record ProfileInfo(String balance, String job, String clan, boolean clans, int passTier, int streak, int points, int rank,
                   boolean dailyReady, int questBox, int passRewards, int parcels, int questsReady, String firstStep, double reputation) {

    private static final int NONE = -1;

    /** Bloquant (base) : hors du thread principal. */
    static ProfileInfo read(UUID player) {
        String balance = EconomyApi.get().map(economy -> economy.format(economy.balance(player))).orElse(null);
        String job = enabled("EterJob") ? Jobs.job(player) : null;
        boolean clans = enabled("EterClan");
        String clan = clans ? Clans.clan(player) : null;
        int passTier = enabled("EterReward") ? Rewards.passTier(player) : NONE;
        int streak = enabled("EterReward") ? Rewards.streak(player) : NONE;
        int points = enabled("EterSuccess") ? Success.points(player) : NONE;
        int rank = points > 0 ? Success.rank(player) : NONE;
        boolean dailyReady = enabled("EterReward") && Rewards.dailyReady(player);
        int questBox = enabled("EterQuest") ? Quests.box(player) : 0;
        int passRewards = enabled("EterReward") ? Rewards.passRewards(player) : 0;
        int parcels = enabled("EterReward") ? Rewards.parcels(player) : 0;
        return new ProfileInfo(balance, job, clan, clans, passTier, streak, points, rank, dailyReady, questBox, passRewards, parcels, 0, null, reputation(player));
    }

    /** Les lignes à afficher, dans la langue du joueur. Thread principal (le nom du métier et du rang viennent d'EterJob). */
    List<Component> lines(Messages messages, Player viewer) {
        List<Component> lines = new ArrayList<>();
        if (balance != null) {
            lines.add(messages.get(viewer, "menu.profile.balance", "value", balance));
        }
        if (job != null) {
            lines.add(job.isEmpty() ? messages.get(viewer, "menu.profile.no-job")
                    : messages.get(viewer, "menu.profile.job", "value", Jobs.name(viewer, job)));
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
        if (reputation > 0) {
            lines.add(messages.get(viewer, "menu.profile.reputation", "value", String.valueOf(Math.round(reputation))));
        }
        if (points != NONE) {
            lines.add(rank > 0 ? messages.get(viewer, "menu.profile.success-rank", "value", String.valueOf(points), "rank", String.valueOf(rank))
                    : messages.get(viewer, "menu.profile.success", "value", String.valueOf(points)));
        }
        // À faire maintenant : ce qui attend le joueur (les boutons concernés brillent, MainMenu)
        if (todo()) {
            lines.add(Component.empty());
            lines.add(messages.get(viewer, "menu.todo.title"));
            if (firstStep != null) {
                lines.add(messages.get(viewer, "menu.todo.first-steps", "step", firstStep));
            }
            if (dailyReady) {
                lines.add(messages.get(viewer, "menu.todo.daily"));
            }
            if (questsReady > 0) {
                lines.add(messages.get(viewer, "menu.todo.quests-ready", "count", String.valueOf(questsReady)));
            }
            if (questBox > 0) {
                lines.add(messages.get(viewer, "menu.todo.quest-box", "count", String.valueOf(questBox)));
            }
            if (passRewards > 0) {
                lines.add(messages.get(viewer, "menu.todo.pass", "count", String.valueOf(passRewards)));
            }
            if (parcels > 0) {
                lines.add(messages.get(viewer, "menu.todo.parcels", "count", String.valueOf(parcels)));
            }
        }
        return lines;
    }

    /** Ce qui se lit sur le thread principal : quêtes prêtes à rendre (objets portés compris), prochaine étape des Premiers pas. */
    ProfileInfo withQuestsReady(Player viewer) {
        int ready = enabled("EterQuest") ? Quests.ready(viewer) : 0;
        String step = enabled("EterSuccess") ? Success.nextStep(viewer) : null;
        return new ProfileInfo(balance, job, clan, clans, passTier, streak, points, rank, dailyReady, questBox, passRewards, parcels, ready, step, reputation);
    }

    /** Quelque chose attend le joueur. */
    boolean todo() {
        return dailyReady || questBox > 0 || passRewards > 0 || parcels > 0 || questsReady > 0 || firstStep != null;
    }

    /** Bloquant : la réputation du réseau (EterLib), 0 si illisible. */
    private static double reputation(UUID player) {
        try {
            return fr.eternom.eterLib.EterLib.get().getReputation().get(player);
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    private static boolean enabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    private static final class Jobs {
        /** "métier|rang" ; "" : pas de métier ; null : rien à lire. */
        static String job(UUID player) {
            return JobApi.get().map(api -> api.job(player).map(job -> job + "|" + api.rank(player, job)).orElse("")).orElse(null);
        }

        /** « Mineur · Artisan ». */
        static String name(Player viewer, String job) {
            String[] parts = job.split("[|]", 2);
            return JobApi.get().map(api -> api.jobName(viewer, parts[0])
                    + (parts.length > 1 ? " · " + api.rankName(viewer, parts[1]) : "")).orElse(parts[0]);
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

        static int passRewards(UUID player) {
            return RewardApi.get().map(api -> api.claimablePassRewards(player)).orElse(0);
        }

        static int parcels(UUID player) {
            return RewardApi.get().map(api -> api.parcels(player)).orElse(0);
        }

        static boolean dailyReady(UUID player) {
            return RewardApi.get().map(api -> !api.claimedToday(player)).orElse(false);
        }
    }

    private static final class Quests {
        static int box(UUID player) {
            return fr.eternom.eterQuest.api.QuestApi.get().map(api -> api.boxCount(player)).orElse(0);
        }

        static int ready(Player player) {
            return fr.eternom.eterQuest.api.QuestApi.get().map(api -> api.readyCount(player)).orElse(0);
        }
    }

    private static final class Success {
        static int points(UUID player) {
            return SuccessApi.get().map(api -> api.points(player)).orElse(NONE);
        }

        static int rank(UUID player) {
            return SuccessApi.get().map(api -> api.rank(player)).orElse(NONE);
        }

        static String nextStep(Player player) {
            return SuccessApi.get().flatMap(api -> api.nextStep(player)).orElse(null);
        }
    }
}
