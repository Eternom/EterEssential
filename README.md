# EterEssential

Les commandes essentielles du réseau : téléportation entre joueurs, `/back`, `/rtp`, informations, argent et outils du
staff. Pas de `/spawn` (futur serveur lobby), de kit de départ ni de messages de connexion (plugins dédiés à venir).
Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.5.0+** (`depend`) : base, langues, menus, joueurs du réseau et **toutes les téléportations**
  (attente, délai commun, combat, départ vers un autre serveur).
- **Redis facultatif.** Avec lui, `/tpa`, `/tp`, `/tphere`, `/broadcast` et les messages de `/pay` traversent les
  serveurs ; sans lui, ils restent sur le serveur où l'on est (rien n'est cassé).
- **Vault + EterEconomy** pour `/money` et `/pay` (sinon : « économie indisponible »). En multi-serveur, EterEconomy
  doit être en `storage.type: redis` avec `information_storage.format: mysql` sur tous les serveurs : en mode `local`,
  chaque serveur garde les soldes en mémoire, et un paiement vers un joueur connecté ailleurs serait écrasé.

## Fonctionnement

**Messages entre serveurs** (`module/network/NetworkBus`) : un canal Redis `eteressential`, un type par message
(`tpa-request`, `tpa-teleport`, `staff-tphere`, `broadcast`, `notify`). Le serveur d'origine ignore le sien.
`notify` envoie un message de langue à un joueur où qu'il soit (accepté, refusé, argent reçu...).
`PlayerLookup` trouve un joueur sur ce serveur puis dans `eter_players`.

**Tpa** (`module/tpa`) : la demande est gardée côté destinataire, dans Redis (hash `tpa:received:<uuid>`, expiration
`tpa.expire-seconds`) ou en mémoire sans Redis, **jamais en base**. Un joueur n'a qu'une demande envoyée à la fois.
À l'acceptation, celui qui voyage part avec **ses** règles d'EterLib ; s'il est sur un autre serveur, `tpa-teleport`
demande à ce serveur de lancer sa téléportation. `/tptoggle` est en base (`eteressential_players`), lu à chaque demande ;
`eteressential.bypass.tptoggle` passe outre.

**/back** (`module/back`) : `EterTeleportEvent` d'EterLib est lancé juste avant chaque départ, même vers un autre
serveur ; `BackListener` y retient la position quittée, et le lieu de la mort avec `eteressential.back.death`.
Stockage commun au réseau : Redis (`back:<uuid>`, 1 jour), sinon la table `eteressential_back`. **Jamais en mémoire** :
chaque serveur aurait sa copie, et on pourrait faire plusieurs `/back` en changeant de serveur.

**/rtp** (`module/rtp`) : menu des mondes de **ce** serveur (`rtp.worlds`, 4 au plus). Délai propre au rtp
(`rtp.cooldown`, Redis sinon table `eteressential_rtp`, même raison que `/back`), en plus du délai commun ; il ne
démarre que si le joueur part vraiment (`TeleportService#teleport(joueur, destination, auDépart)`). Recherche d'un
endroit : chunk chargé en tâche de fond par Paper, point au hasard dans l'anneau `min-radius`-`max-radius` autour du
spawn du monde, sol solide sans danger, pieds et tête libres, sous le plafond dans le Nether ; `rtp.attempts` essais.

**Staff** (`module/staff`) : `/tp` et `/tphere` utilisent `teleportNow` d'EterLib (immédiat, sans règles) et
fonctionnent sur tout le réseau. `/invsee` et `/endersee` ouvrent l'inventaire réel (modifiable) d'un joueur de **ce**
serveur. `/broadcast` lit le texte en MiniMessage (commande réservée au staff).

## Commandes et permissions

| Commande | Permission | Par défaut |
|---|---|---|
| `/tpa`, `/tpaccept` (`tpyes`), `/tpdeny` (`tpno`), `/tpacancel`, `/tptoggle` | `eteressential.tpa` | tous |
| `/tpahere` | `eteressential.tpahere` | tous |
| `/back` | `eteressential.back` (+ `eteressential.back.death` pour la mort) | op |
| `/rtp` (`wild`) | `eteressential.rtp` | tous |
| `/list`, `/find`, `/seen` | `eteressential.list`, `.find`, `.seen` | op |
| `/money [joueur]` (`bal`), `/pay` | `eteressential.money`, `.pay` ; autre joueur : `eteressential.others.money` | tous / op |
| `/gm`, `/gmc`, `/gms`, `/gma`, `/gmsp`, `/fly`, `/heal`, `/feed`, `/speed`, `/clear` | `eteressential.<commande>` (`gamemode` pour /gm) ; sur un autre : `eteressential.others.<commande>` | op |
| `/tp`, `/tphere`, `/invsee`, `/endersee`, `/broadcast` (`bc`) | `eteressential.<commande>` | op |

Dispenses : `eteressential.bypass.tptoggle`, `eteressential.bypass.rtp` (sous `eteressential.bypass.*`).
`eteressential.admin` regroupe tout.

## Données

- `eteressential_players` : `uuid`, `tpa_disabled`.
- Sans Redis seulement : `eteressential_back` (dernière position) et `eteressential_rtp` (fin du délai, nettoyée au démarrage).
