package fr.eternom.eterEssential.api;

import fr.eternom.eterLib.module.teleport.Destination;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;

/**
 * Ce qu'EterEssential offre aux autres plugins : la position de retour (/back) d'un joueur et s'il accepte les
 * demandes de tpa. Personne d'autre ne lit ses clés Redis ni eteressential_players : on demande ici.
 * <pre>
 *     // compileOnly("com.github.Eternom:EterEssential:&lt;tag&gt;") ; plugin.yml : softdepend: [EterEssential]
 * </pre>
 */
public interface EssentialApi {

    /** L'API d'EterEssential si le plugin tourne sur ce serveur. */
    static Optional<EssentialApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(EssentialApi.class));
    }

    /** La dernière position quittée (/back), sur n'importe quel serveur. Bloquant (Redis) : hors du thread principal. */
    Optional<Destination> back(UUID player);

    /** Le joueur accepte les demandes de tpa (pas de /tptoggle). Bloquant (base). */
    boolean acceptsTpa(UUID player);
}
