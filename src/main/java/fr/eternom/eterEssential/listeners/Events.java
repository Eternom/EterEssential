package fr.eternom.eterEssential.listeners;

import fr.eternom.eterEssential.Main;
import fr.eternom.eterEssential.module.back.BackListener;
import fr.eternom.eterLib.EterLib;

public class Events {

    public Events(Main main) {
        main.getServer().getPluginManager().registerEvents(
                new BackListener(main, main.getBack(), EterLib.get().getServerName()), main);
    }
}
