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
        // Déconnexion en combat contre un joueur : mort sur place (durée du combat : EterLib teleport.combat-tag)
        CombatLog combatLog = main.getConfig().getBoolean("combat-log.enabled", true) ? new CombatLog() : null;
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
