package fr.eternom.eterEssential.module.economy;

import fr.eternom.eterSuccess.api.SuccessApi;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * /pay réservé aux comptes qui ont assez joué (pay.min-playtime-hours, temps de jeu actif de /stats d'EterSuccess) :
 * un second compte créé pour récolter récompenses du jour, kits et votes ne peut pas tout envoyer au compte principal.
 * Sans EterSuccess sur ce serveur, pas de limite.
 */
final class PayGate {

    private final long minMinutes;

    PayGate(double minHours) {
        this.minMinutes = Math.round(Math.max(0, minHours) * 60);
    }

    long minMinutes() {
        return minMinutes;
    }

    /** Minutes de jeu encore nécessaires (0 : /pay permis). Bloquant (base). */
    long minutesLeft(UUID player) {
        if (minMinutes <= 0 || !Bukkit.getPluginManager().isPluginEnabled("EterSuccess")) {
            return 0;
        }
        long played = SuccessApi.get().map(api -> api.playtimeMinutes(player)).orElse(minMinutes);
        return Math.max(0, minMinutes - played);
    }
}
