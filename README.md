# EterEssential

Les commandes essentielles du réseau : téléportation entre joueurs, `/back`, informations, argent et outils du
staff. Ailleurs : `/rtp` (EterRtp, seulement sur les serveurs de survie), `/spawn` et `/lobby` (EterHub, EterVelocityLobby), messages d'arrivée et de départ du réseau
(EterVelocityLobby) ; pas de kit de départ.
Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.9.1+** (`depend`, textes communs, cadre des menus, bus réseau, `Money`) : base, langues, menus, joueurs du réseau et **toutes les téléportations**
  (attente, délai commun, combat, départ vers un autre serveur).
- **Redis** (obligatoire, via EterLib) : `/tpa`, `/tp`, `/tphere` et les messages de `/pay` traversent les
  serveurs.
- **Vault + EterEconomy 2.0+** pour `/money`, `/pay` et `/eco` (sinon : « économie indisponible »). EterEconomy garde les
  soldes en base, mouvements atomiques : rien à régler pour le multi-serveur.

## Fonctionnement

**Messages entre serveurs** (`NetworkBus` d'EterLib, `lib.network`) : un canal Redis `eteressential`, un type par message
(`tpa-request`, `tpa-teleport`, `staff-tphere`, `notify`). Le serveur d'origine ignore le sien.
`notify` envoie un message de langue à un joueur où qu'il soit (accepté, refusé, argent reçu...).
`PlayerLookup` trouve un joueur sur ce serveur puis dans `eter_players` ; un invisible (vanish du staff, EterLib 1.9.1) est
« hors ligne » pour qui ne peut pas le voir (`/tpa`, `/find`, `/seen`, `/list`).

**Tpa** (`module/tpa`) : la demande est gardée côté destinataire, dans Redis (hash `tpa:received:<uuid>`, expiration
`tpa.expire-seconds`), **jamais en base**. Un joueur n'a qu'une demande envoyée à la fois.
À l'acceptation, celui qui voyage part avec **ses** règles d'EterLib ; s'il est sur un autre serveur, `tpa-teleport`
demande à ce serveur de lancer sa téléportation. `/tptoggle` est en base (`eteressential_players`), lu à chaque demande ;
`eteressential.bypass.tptoggle` passe outre.

**/back** (`module/back`) : `EterTeleportEvent` d'EterLib est lancé juste avant chaque départ, même vers un autre
serveur ; `BackListener` y retient la position quittée, et le lieu de la mort avec `eteressential.back.death`.
Stockage commun au réseau : Redis (`back:<uuid>`, 1 jour). **Jamais en mémoire** :
chaque serveur aurait sa copie, et on pourrait faire plusieurs `/back` en changeant de serveur.


**Staff** (`module/staff`) : `/tp` et `/tphere` utilisent `teleportNow` d'EterLib (immédiat, sans règles) et
fonctionnent sur tout le réseau. `/invsee` et `/endersee` ouvrent l'inventaire réel (modifiable) d'un joueur de **ce**
serveur. Les annonces (`/broadcast`) sont sur le proxy : EterVelocityBroadcast.

**Mort** (`module/death/DeathPenalty`) : mourir fait perdre `death.money-loss-percent` (5 %) de son solde, arrondi à l'unité
inférieure ; l'argent disparaît (évier de l'économie). Un pourcentage : les grosses fortunes le sentent aussi. Retrait
par Vault en tâche de fond. Dispense : `eteressential.bypass.deathloss`. La future banque de clan sera à l'abri.

## Commandes et permissions

| Commande | Permission | Par défaut |
|---|---|---|
| `/tpa`, `/tpaccept` (`tpyes`), `/tpdeny` (`tpno`), `/tpacancel`, `/tptoggle` | `eteressential.tpa` | tous |
| `/tpahere` | `eteressential.tpahere` | tous |
| `/back` | `eteressential.back` (+ `eteressential.back.death` pour la mort) | op |
| `/list`, `/find`, `/seen` | `eteressential.list`, `.find`, `.seen` | op |
| `/money [joueur]` (`bal`), `/pay` | `eteressential.money`, `.pay` ; autre joueur : `eteressential.others.money` | tous / op |
| `/eco give\|take\|set\|reset <joueur> [montant]` (aussi depuis la console) | `eteressential.eco` | op |
| `/gm`, `/gmc`, `/gms`, `/gma`, `/gmsp`, `/fly`, `/heal`, `/feed`, `/speed`, `/clear` | `eteressential.<commande>` (`gamemode` pour /gm) ; sur un autre : `eteressential.others.<commande>` | op |
| `/tp`, `/tphere`, `/invsee`, `/endersee` | `eteressential.<commande>` | op |

Dispense : `eteressential.bypass.tptoggle` (sous `eteressential.bypass.*`).
`eteressential.admin` regroupe tout.

Vol : le droit de voler est gardé dans les données du joueur sur chaque serveur. À la connexion, un joueur en survie ou
aventure sans `eteressential.fly` le perd (`FlightReset`, au plus tôt) : un /fly donné par le staff ne dure qu'une
session, et un plugin de lobby installé par erreur (double saut d'EterHub) ne laisse pas voler en survie.

## Données

- `eteressential_players` : `uuid`, `tpa_disabled`.
