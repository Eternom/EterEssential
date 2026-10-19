# EterEssential

Les commandes essentielles du réseau : téléportation entre joueurs, `/back`, établis et four portatifs, informations,
argent et outils du staff. Ailleurs : `/rtp` (EterRtp, seulement sur les serveurs de survie), `/spawn` et `/lobby` (EterHub, EterVelocityLobby), messages d'arrivée et de départ du réseau
(EterVelocityLobby), kits (EterKit, bouton « Kits » au centre du menu principal).
Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.19.0+** (`depend`, combat contre un joueur (`CombatTracker#opponent`), textes communs, cadre des menus, bus réseau, `Money`) : base, langues, menus, joueurs du réseau et **toutes les téléportations**
  (attente, délai commun, combat, départ vers un autre serveur).
- **Redis** (obligatoire, via EterLib) : `/tpa`, `/tp`, `/tphere` et les messages de `/pay` traversent les
  serveurs.
- **EterEconomy 2.2.2+** (`depend`, son API `EconomyApi`) pour `/money`, `/pay`, `/eco` et la perte à la mort. Soldes en
  base, mouvements atomiques : rien à régler pour le multi-serveur.
- **EterSync 1.2.1+** (facultatif) : `/invsee` et `/endersee` d'un joueur ailleurs ou hors ligne.
- **EterMarket, EterClan, EterReward, EterSuccess** (facultatifs, `softdepend`) : les infos de la tête de `/menu`.

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


**Menu central** (`module/menu`) : `/menu` (`/profil`, ou accroupi + F : `MenuShortcut`, `menu.shortcut`), une porte
vers tout le reste. Page principale : les catégories (sous-menus Progression, Voyager, Social, Économie, Outils) sur la
2e ligne, puis 5 raccourcis vers ce qui sert le plus souvent (homes, spawn, rtp, récompense du jour, pass),
rien que des commandes permises à tous par défaut ; Outils n'apparaît qu'à qui a au moins un outil (grade, pass) : peu de boutons, 5 lignes, la page reste facile à lire. Tout est réglé dans `config.yml > menu`. Un bouton lance la
commande d'un autre plugin ou ouvre un sous-menu ; il n'est montré que s'il sert ici (commande présente et permise,
sous-menu non vide) ; si un bouton d'une ligne est caché, les autres de la ligne se recentrent, symétriques (un nombre pair laisse la case du milieu vide). `hide-on-lobby` cache un bouton sur un lobby : à la même case, Spawn sur un lobby, « Retour au lobby » ailleurs (`network/LobbyCommand` : /lobby est une commande du proxy, qu'un menu ne peut pas lancer ; le serveur la demande au proxy par le canal `eter:lobby`, EterVelocityLobby 1.2.5+). Une commande avec `{player}` fait choisir un joueur connecté (`PlayerPicker`, noms d'EterLib sans
les invisibles), `{amount}` et `{text}` sont demandés dans un Dialog (`MenuInputs`) : `/pay`, `/tpa`, `/msg` sans rien
taper. Annuler ou une saisie invalide **ramène à la page**, jamais hors du menu. La tête du joueur affiche ses infos
(`ProfileInfo`, lues en tâche de fond par l'API d'EterEconomy, EterMarket, EterClan, EterReward et EterSuccess, chacune
dans sa classe : un plugin absent ici n'affiche simplement pas sa ligne). `/menu <sous-menu>` ouvre un sous-menu : le
bouton Retour des autres plugins y ramène (`back-command: "menu progression"`...), et un `back-command` vide ramène au
menu central (EterLib 1.11.1) : on navigue partout sans taper de commande.

**Établis portatifs** (`module/station/StationCommand`) : `/craft`, `/anvil`, `/smithing`, `/grindstone`,
`/stonecutter` ouvrent le menu du bloc par `MenuType`, **rattaché à la position du joueur sans vérifier le bloc** : à la
fermeture, ce qui reste dans la grille est rendu au joueur (sans position, le jeu le perdrait). `/enderchest` ouvre son
propre coffre de l'Ender (le même que le bloc, synchronisé entre les serveurs par EterSync 1.2.0+). Commandes
d'**usage** : rien n'y reste, on peut donc les donner pour une durée (grade, pass d'EterReward).

**Four portatif** (`module/furnace`) : `/furnace`, un vrai four propre au joueur, le même sur tout le réseau
(`eteressential_furnaces`), qui **cuit même fermé, joueur déconnecté ou serveur éteint**. Le menu est celui du four, sans
bloc : rien ne le fait cuire tout seul. `PortableFurnace` le fait avancer tick par tick tant qu'il est ouvert ; à la
fermeture, toutes les 5 s pendant qu'il est ouvert (un plantage ne laisse pas en base ce que le joueur a déjà sorti) et
à l'arrêt du serveur, le contenu part en base avec l'heure (un seul fil d'écriture : dans l'ordre), et le menu est
vidé à la fermeture. Verrou Redis `furnace:lock:<uuid>` (30 s, prolongé à chaque enregistrement, rendu après le
dernier) : un joueur qui change de serveur four ouvert ne peut pas le rouvrir ailleurs avant que la base soit à jour ; à l'ouverture,
`FurnaceSimulation` rattrape le temps écoulé d'un coup (7 jours au plus), par bonds. La simulation reprend les règles
du jeu : recettes du four (datapacks compris), durée de chaque combustible, seau de lave qui laisse un seau, cuisson qui
recule quand le feu s'éteint, nouvel objet = cuisson à zéro. L'expérience des recettes est donnée à la fermeture.
Le contenu reste si la permission est retirée : il revient avec elle.

**Staff** (`module/staff`) : `/tp` et `/tphere` utilisent `teleportNow` d'EterLib (immédiat, sans règles) et
fonctionnent sur tout le réseau. `/invsee` et `/endersee` ouvrent l'inventaire réel (modifiable) d'un joueur de **ce**
serveur ; `/invsee` et `/endersee` d'un joueur ailleurs ou hors ligne montrent sa dernière sauvegarde (inventaire ou coffre de l'Ender), en lecture seule (EterSync 1.2.1+). Les annonces (`/broadcast`) sont sur le proxy : EterVelocityBroadcast.

**Mort** (`module/death/DeathPenalty`) : mourir fait perdre `death.money-loss-percent` (5 %) de son solde, arrondi à l'unité
inférieure ; l'argent disparaît (évier de l'économie). Un pourcentage : les grosses fortunes le sentent aussi. Retrait
par EterEconomy en tâche de fond. Dispense : `eteressential.bypass.deathloss`. La banque d'EterClan est à l'abri.

**Déconnexion en combat** (`module/death/CombatLog`, 1.6.0) : se déconnecter pendant un combat contre un **joueur** (les
monstres ne comptent pas ; durée : `teleport.combat-tag` d'EterLib, `CombatTracker#opponent`, EterLib 1.19.0) = mort sur place (`setHealth(0)` en `LOWEST`, avant
qu'EterSync n'enregistre l'inventaire en `MONITOR` : objets et expérience au sol, rien de dupliqué), avec la perte d'argent et
l'annonce de cause `combat-log` (`<killer>` = l'adversaire fui). Pas pour un joueur expulsé (`QuitReason.KICKED` :
redémarrage, staff), en créatif ou spectateur. Dispense : `eteressential.bypass.combatlog`. `combat-log.enabled: false` désactive.

**Annonce des morts** (`module/death/DeathAnnouncer`, `DeathCause`) : la cause est lue dans le `DamageSource` de Paper et
réduite à une famille (`lava`, `fall`, `creeper`, `player`, `mob`...). À la place du message de Minecraft, une variante
au hasard de `lang/ > death.public.<cause>` (`<player>`, `<killer>` : joueur, ou créature traduite chez chaque joueur),
sur tout le réseau (`death.announce: network`, bus `eteressential`, type `death` ; un même `seed` choisit la variante dans
chaque langue), sur ce serveur (`server`) ou pas du tout (`none` : message de Minecraft). Rien de public pour un joueur
invisible. Pour le mort seulement : un son propre à la cause (par défaut dans le code, `death.sounds.<cause>` pour en
changer), une pique suivie d'une vraie astuce (`death.tips.<cause>`) et sa position (avec `/back` s'il a
`eteressential.back.death`).

## Commandes et permissions

| Commande | Permission | Par défaut |
|---|---|---|
| `/tpa`, `/tpaccept` (`tpyes`), `/tpdeny` (`tpno`), `/tpacancel`, `/tptoggle` | `eteressential.tpa` | tous |
| `/tpahere` | `eteressential.tpahere` | tous |
| `/menu [sous-menu]` (`profil`) | `eteressential.menu` | tous |
| `/pay` (limite) | après `pay.min-playtime-hours` (5) heures de jeu actif (`PayGate`, temps de `/stats` d'EterSuccess 1.5.0+ : `SuccessApi#playtimeMinutes`) : un second compte ne peut pas envoyer ses récompenses au principal | |
| `/vote` | `eteressential.vote` | tous — un lien vers la page de vote du site (`vote.page`), jamais vers les sites de vote : seule la page du site compte le vote et donne la récompense |
| `/back` | `eteressential.back` (+ `eteressential.back.death` pour la mort) | op |
| `/craft` (`workbench`, `wb`), `/anvil`, `/smithing`, `/grindstone`, `/stonecutter`, `/enderchest` (`ec`), `/furnace` | `eteressential.<commande>` | op (à donner par grade ou pour une durée) |
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
- `eteressential_furnaces` : `uuid`, `input`, `fuel`, `output` (objets sérialisés), `cook`, `lit`, `lit_total` (ticks),
  `xp` (pas encore donnée), `saved_at` (heure de la dernière mise à jour, pour rattraper la cuisson).

## API (pour les autres plugins)

`fr.eternom.eterEssential.api.EssentialApi` (`EssentialApi.get()`) : personne d'autre ne lit ses clés Redis (`back:`,
`tpa:`) ni `eteressential_players`.

- `back(uuid)` : la dernière position quittée (bloquant) ;
- `acceptsTpa(uuid)` : pas de `/tptoggle` (bloquant).

Ce qu'EterEssential demande aux autres : l'**argent** à EterEconomy (`EconomyApi`, sources « EterEssential · /pay »,
« · mort », « · staff (/eco) ») ; le **dernier inventaire** d'un joueur ailleurs ou hors ligne à EterSync (`SyncApi`,
`/invsee` en lecture seule) ; la recherche d'un joueur qui respecte le vanish à EterLib (`findFor`).
