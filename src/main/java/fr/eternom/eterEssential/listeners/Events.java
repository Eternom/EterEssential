package fr.eternom.eterEssential.listeners;

import fr.eternom.eterEssential.Main;
import fr.eternom.eterEssential.module.back.BackListener;
import fr.eternom.eterEssential.module.death.DeathPenalty;
import fr.eternom.eterLib.EterLib;

public class Events {

    public Events(Main main) {
        main.getServer().getPluginManager().registerEvents(
                new BackListener(main, main.getBack(), EterLib.get().getServerName()), main);
        main.getServer().getPluginManager().registerEvents(new DeathPenalty(main, main.getMessages(),
                Math.clamp(main.getConfig().getDouble("death.money-loss-percent", 5), 0, 100)), main);
    }
}
