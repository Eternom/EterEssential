package fr.eternom.eterEssential.module.back;

import fr.eternom.eterEssential.api.EssentialApi;
import fr.eternom.eterEssential.module.tpa.TpaSettings;
import fr.eternom.eterLib.module.teleport.Destination;

import java.util.Optional;
import java.util.UUID;

/** L'API d'EterEssential (EssentialApi) : le plugin lui-même, vu de l'extérieur. */
public class EssentialApiService implements EssentialApi {

    private final BackStore back;
    private final TpaSettings tpa;

    public EssentialApiService(BackStore back, TpaSettings tpa) {
        this.back = back;
        this.tpa = tpa;
    }

    @Override
    public Optional<Destination> back(UUID player) {
        return back.get(player, "back");
    }

    @Override
    public boolean acceptsTpa(UUID player) {
        return !tpa.isDisabled(player);
    }
}
