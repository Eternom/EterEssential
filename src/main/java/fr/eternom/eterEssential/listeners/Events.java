package fr.eternom.eterEssential.listeners;

import fr.eternom.eterEssential.Main;
import fr.eternom.eterEssential.module.back.BackListener;
import fr.eternom.eterEssential.module.death.CombatLog;
import fr.eternom.eterEssential.module.death.DeathAnnouncer;
import fr.eternom.eterEssential.module.death.DeathPenalty;
import fr.eternom.eterEssential.module.menu.MenuShortcut;
import fr.eternom.eterEssential.module.staff.FlightReset;
import fr.eternom.eterLib.EterLib;

public class Events {

    public Events(Main main) {
        main.getServer().getPluginManager().registerEvents(
                new BackListener(main, main.getBack(), EterLib.get().getServerName()), main);
        main.getServer().getPluginManager().registerEvents(new DeathPenalty(main, main.getMessages(),
                Math.clamp(main.getConfig().getDouble("death.money-loss-percent", 5), 0, 100)), main);
        // Déconnexion en combat contre un joueur : mort sur place (combat-log.seconds après le dernier coup ; 0 = désactivé)
        int combatSeconds = main.getConfig().getInt("combat-log.seconds", 15);
        CombatLog combatLog = combatSeconds > 0 ? new CombatLog(combatSeconds) : null;
        if (combatLog != null) {
            main.getServer().getPluginManager().registerEvents(combatLog, main);
        }
        main.getServer().getPluginManager().registerEvents(new DeathAnnouncer(main, main.getMessages(), main.getBus(),
                main.getConfig().getConfigurationSection("death"), combatLog), main);
        main.getServer().getPluginManager().registerEvents(new FlightReset(), main);
        main.getServer().getPluginManager().registerEvents(main.getFurnace(), main);
        if (main.getConfig().getBoolean("menu.shortcut", true)) {
            main.getServer().getPluginManager().registerEvents(new MenuShortcut(), main);
        }
    }
}
