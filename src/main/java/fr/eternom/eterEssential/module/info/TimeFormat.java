package fr.eternom.eterEssential.module.info;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.CommandSender;

/** Durée lisible dans la langue du joueur, avec les deux plus grandes unités : "2 j 3 h", "30 min 5 s", "12 s". */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String format(Messages messages, CommandSender receiver, long seconds) {
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        if (days > 0) {
            return messages.plain(receiver, "time.days-hours", "days", String.valueOf(days), "hours", String.valueOf(hours));
        }
        if (hours > 0) {
            return messages.plain(receiver, "time.hours-minutes", "hours", String.valueOf(hours), "minutes", String.valueOf(minutes));
        }
        if (minutes > 0) {
            return messages.plain(receiver, "time.minutes-seconds", "minutes", String.valueOf(minutes), "seconds", String.valueOf(rest));
        }
        return messages.plain(receiver, "time.seconds", "seconds", String.valueOf(rest));
    }
}
