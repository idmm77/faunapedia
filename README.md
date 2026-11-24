# FaunaPedia — mod NeoForge 1.21.1

Un mod autonome qui ajoute une **encyclopédie vivante des mobs** : chaque joueur découvre
progressivement les types de mobs qu'il croise, qu'ils viennent de Minecraft ou de **n'importe quel
autre mod chargé**.

Aucune liste de mobs n'est codée en dur : le mod parcourt le registre `EntityType` à l'exécution.

---

## Démarrage rapide

```bash
# Nécessite un JDK 21
./gradlew runClient      # lance un client de développement avec le mod
./gradlew build          # produit build/libs/faunapedia-1.0.0.jar
```

**Touche par défaut : `P`** (réassignable dans Options → Commandes → catégorie « FaunaPedia »).

---

## Comment tester en jeu

1. `./gradlew runClient`
2. Créer un monde et s'approcher d'un mob (une vache, un zombie…) en le regardant sans obstacle.
3. Un **toast** apparaît en haut à droite : « Nouveau mob découvert : … ».
4. Appuyer sur **`P`** pour ouvrir le FaunaPedia : le mob croisé est en couleur avec son nom, les autres
   restent grisés avec « ??? ».
5. Quitter le monde, le rouvrir : la progression est conservée.

---

## Architecture

| Élément | Emplacement | Rôle |
|---|---|---|
| Classe principale | `FaunaPedia.java` | Point d'entrée, enregistrements |
| Configuration | `FaunaPediaConfig.java` | Deux specs : CLIENT (toasts, sons) et SERVER (détection) |
| Données joueur | `data/FaunaPediaData.java` | Ensemble des mobs découverts + drapeau de récompense |
| Data Attachment | `data/ModAttachments.java` | Attache les données au joueur (persistant) |
| Détection | `event/EncounterHandler.java` | Scan périodique côté serveur |
| Réseau | `network/` | Deux paquets serveur → client |
| Fiches serveur | `server/MobInfoService.java` | Butins + biomes, avec contrôle d'accès |
| Catalogue | `client/EntityCatalog.java` | Construction dynamique + classification |
| Icônes | `client/EntityIconRenderer.java` | Rendu 3D des mobs + liste noire de sécurité |
| Écran | `client/screen/FaunaPediaScreen.java` | Menu collection défilant |
| Fiche | `client/screen/MobDetailScreen.java` | Détail d'un mob découvert |
| Cache fiches | `client/ClientMobInfoCache.java` | Une requête par mob et par session |
| Toast | `client/toast/DiscoveryToast.java` | Notification de découverte |

### Remonter d'un objet vers les mobs

Deux entrées pour une même question, servies par un unique index inversé bâti côté serveur
(`DropIndexService`) :

- **Dans l'inventaire**, survoler un objet et presser la touche du FaunaPedia ouvre la liste des mobs
  qui le lâchent — on part de l'objet qu'on **a**.
- **Dans le catalogue**, la recherche accepte un nom d'objet — on part de l'objet qu'on **veut**.

L'index échantillonne toutes les tables de butin une fois par monde, puis reste en cache et sert tous
les joueurs ; seul le filtrage final est propre à chacun. Ce filtrage est fait **côté serveur** : un
mob non découvert n'apparaît jamais dans l'index envoyé, sinon chercher un objet rare révélerait
l'existence de son propriétaire. Filtrer côté client n'aurait rien protégé — la donnée aurait déjà
été transmise.

### Le serveur est la source de vérité

La progression vit **uniquement côté serveur**, dans un *Data Attachment* NeoForge (le système
moderne qui remplace les *Capabilities* dépréciées). Le client n'a qu'un cache d'affichage.

Quatre paquets descendants et deux montants. Les montants ne transportent **aucune donnée que le
serveur croirait sur parole** : ce sont de simples sollicitations (« ouvre-moi cette fiche »,
« envoie-moi l'index »), que le serveur valide et filtre avant de répondre. Aucune triche possible.

Les deux paquets structurants de la progression, tous deux **serveur → client** :

- `SyncFaunaPediaPayload` — collection complète, envoyée **une seule fois par connexion**. C'est le
  seul moment où l'ensemble entier transite.
- `DiscoverEntityPayload` — **un seul identifiant**, envoyé en delta à chaque nouvelle découverte.

Il n'y a jamais de resynchronisation complète après la connexion : ni à la réapparition ni au
changement de dimension (le cache client est statique et survit à ces événements ; seule une vraie
déconnexion le vide). L'application des deltas est **idempotente** — recevoir deux fois la même
découverte est sans effet.

Les paquets sont déclarés **optionnels** (`registrar.optional()`) : un serveur avec FaunaPedia
n'oblige pas ses clients à l'installer, et un client équipé peut rejoindre un serveur qui ne l'a pas,
sans être expulsé. Chaque envoi serveur → client est gardé par `hasChannel` (`ModNetwork.sendToClient`)
pour ne rien transmettre à un client dépourvu du mod ; sa progression reste enregistrée côté serveur
et lui parviendra s'il installe le mod plus tard.

### Versionnage des données sauvegardées

Le Data Attachment porte un champ `dataVersion`. À la lecture, un point de migration
(`FaunaPediaData.migrate`) met à niveau les anciens formats sans perte ; une sauvegarde dépourvue du
champ est traitée comme la version 0 (format de base). À l'écriture, la version courante est toujours
estampillée, donc les données se mettent à niveau au premier enregistrement.

### Détection de rencontre

Toutes les `scanIntervalTicks` (20 par défaut, soit 1 seconde), le serveur regarde les `Mob` vivants
dans un rayon de `scanRadius` blocs autour de chaque joueur, et ne retient que ceux avec lesquels le
joueur a une **ligne de vue dégagée**.

**Optimisation :** avant de lancer le raycast de ligne de vue (l'opération la plus coûteuse du scan),
on vérifie si le type a déjà été comptabilisé il y a moins de 30 secondes. Si oui, on l'ignore sans
raycast — en régime permanent, on est presque toujours entouré de mobs déjà vus récemment, ce qui
supprime l'essentiel des raycasts. Le compteur de rencontres continue de monter passé ce délai, donc
le succès « Vieille connaissance » reste atteignable.

Le filtre `instanceof Mob` exclut automatiquement les projectiles, items, bateaux et porte-armures,
tout en incluant tous les mobs moddés (qui héritent de `Mob`).

### Classification générique

Pour chaque `EntityType` du registre, le client instancie **une fois** un exemplaire jetable (jamais
ajouté au monde, immédiatement écarté) afin de tester son type Java réel. C'est la seule façon
fiable de classer aussi les mobs moddés, qui héritent des classes vanilla.

Priorité : **Boss → Aquatiques → Volants → Hostiles → Passifs/Neutres → Autres**.

Les boss sont testés en premier : l'ender dragon implémente `Enemy` et finirait sinon rangé parmi
les hostiles ordinaires. Ils forment **leur propre catégorie**, distincte d'« Autres » : une
découverte de boss est un événement de jeu, pas un cas inclassable. La détection s'appuie sur le tag NeoForge `c:bosses`, auquel les mods
peuvent ajouter leurs propres boss — elle reste donc générique — avec repli sur les deux boss vanilla
si le tag est absent.

Si l'instanciation échoue (mod tiers au constructeur capricieux), on retombe silencieusement sur la
`MobCategory` déclarée. Le résultat est mis en cache : l'opération n'a lieu qu'une fois par session.

---

## Fonctionnalités du menu

- Liste défilante groupée par catégorie, avec compteur et **pourcentage par section**.
- Compteur global **X / Y** + pourcentage + barre de progression.
- Mobs non rencontrés : silhouette grise et nom masqué (« ??? »).
- **Barre de recherche**. Un mob non découvert ne peut être trouvé que par le **nom de son mod**,
  jamais par son propre nom — chercher ne doit pas révéler ce que le joueur n'a pas débloqué.
  La recherche porte aussi sur les **butins** des mobs découverts : taper « cuir » fait remonter ceux
  qui en lâchent. Le butin n'est testé qu'après la garde de découverte, donc chercher « étoile du
  Nether » ne révèle pas l'existence du Wither à qui ne l'a jamais croisé.
- **Bouton de tri** : A → Z, ou regroupement par mod d'origine à l'intérieur de chaque catégorie.
- **Bouton de filtre** : Tous → Vus → Non vus.
- Barre de défilement **cliquable et glissable**, en plus de la molette.
- Tri, filtre et recherche sont **conservés** d'une ouverture à l'autre (remis à zéro en changeant
  de partie).
- **Échap remonte d'un niveau** au lieu de tout fermer : depuis la liste des biomes on revient à la
  fiche, depuis la fiche à la liste des mobs. Même comportement que le bouton « Retour », et la
  position de défilement est préservée.
- Les butins affichent leur **infobulle** au survol.
- Le mod d'origine (namespace) est affiché sous chaque entrée, y compris pour les mobs inconnus.
- **Icônes réelles** : chaque mob découvert est affiché par un vrai rendu 3D de l'entité.

---

## Icônes : aucune image à fournir

Le mod n'embarque **aucune texture**. L'icône d'un mob est obtenue en effectuant un rendu 3D de
l'entité elle-même : c'est donc le *renderer* enregistré par le mod d'origine qui dessine le mob.
Conséquence directe : **les mobs de n'importe quel mod ont automatiquement la bonne icône**, sans
travail d'intégration.

Si le renderer d'un mod échoue hors contexte de monde, ce type est mis en liste noire (une seule
tentative) et remplacé par une pastille de couleur stable. Un mod mal écrit ne peut pas casser
l'écran.

Les mobs **non découverts** ne sont volontairement pas dessinés, même en silhouette : la forme d'un
mob suffit souvent à l'identifier, ce qui viderait la découverte de son intérêt.

### Exemplaires paresseux (culling)

Les exemplaires d'entités servant au rendu 3D sont gérés par `MobSampleCache` : un mob n'est instancié
qu'au moment où sa ligne devient **visible** à l'écran, et son exemplaire est **recyclé** dès qu'il
quitte le viewport depuis quelques secondes. Aucun exemplaire n'est jamais créé pour un mob non
découvert (on ne dessine pas son modèle). On évite ainsi de garder des centaines d'entités vivantes en
mémoire, comme c'était le cas quand tout le catalogue était instancié d'un bloc. La classification,
elle, instancie chaque type une fois mais de façon transitoire — l'exemplaire est jeté aussitôt après
le test `instanceof`.

---

## Fiche détaillée

Un clic sur un mob **déjà rencontré** ouvre sa fiche :

| Donnée | Source |
|---|---|
| Points de vie, armure, dégâts, vitesse, taille | Attributs de l'entité |
| Butins possibles | Table de butin, échantillonnée côté serveur |
| Biomes d'apparition naturelle | Registre des biomes du monde chargé |
| Première rencontre (jour Minecraft) | Enregistrée à la découverte |
| Nombre de rencontres | Compteur, voir ci-dessous |

Un clic sur l'en-tête « Biomes d'apparition » ouvre la **liste complète et défilante**, groupée par
mod d'origine.

### Le compteur de rencontres

Un délai de **30 secondes par type de mob** sépare deux incréments. Sans lui, rester à côté d'une
vache ferait grimper le compteur d'un point par seconde : on compterait des secondes de présence, pas
des rencontres.

Date et compteur voyagent dans le paquet de fiche, **pas** dans la synchronisation générale : ils ne
sont lus qu'à l'ouverture d'une fiche. Un compteur synchronisé en continu aurait généré du trafic
réseau permanent pour une information consultée rarement.

**Le verrouillage est réel, pas cosmétique.** La fiche est construite par le serveur, qui vérifie que
le joueur a bien découvert le mob avant de répondre. Un client modifié ne peut pas extraire les
informations d'un mob non débloqué.

Deux limites assumées :

- Les butins sont **estimés par échantillonnage** (80 tirages de la table). Une table de butin ne se
  « lit » pas de façon fiable — ce sont des conditions imbriquées, souvent personnalisées par les
  mods. L'échantillonnage marche pour n'importe quelle table, y compris moddée, mais un drop
  extrêmement rare peut ne pas apparaître.
- Le tirage simule une **mise à mort par un joueur**, sans enchantement Butin. Beaucoup de tables
  portent une condition `killed_by_player` : sans joueur dans le contexte, un blaze ne lâcherait
  aucun bâton.
- Les objets lâchés **par du code** et non par une table de butin sont invisibles à cette méthode.
  Sept mobs vanilla sont concernés (`dropCustomDeathLoot`), la plupart pour des cas exotiques
  (disque du creeper tué par un squelette, bloc porté par l'enderman, équipement des zombies et
  squelettes). Le seul cas notable est le **Wither** : sa table de butin est littéralement vide,
  l'étoile du Nether étant lâchée par code. Les découvrir automatiquement exigerait de tuer
  réellement le mob, avec tous les effets de bord que cela implique.

  Le Wither fait donc l'objet de la **seule exception codée en dur du mod**, isolée dans
  `MobInfoService.codeOnlyDrops()`. La limite reste entière : un boss moddé lâchant son butin par
  code n'y figurera pas, et sa fiche restera incomplète sans que rien ne le signale. Pour les autres
  mobs sans butin de table, le message « Aucun butin dans sa table de butin » reste volontairement
  précis plutôt que d'affirmer que le mob ne lâche rien.
- Les biomes ne couvrent que les apparitions **naturelles par biome**. Un mob lié à une structure
  (squelette wither en forteresse) ou uniquement invocable n'affiche aucun biome.

Une fiche n'est demandée qu'une fois par session, puis mise en cache côté client.

La liste des biomes est bornée par la place réellement disponible au-dessus du bouton « Retour », et
complétée par un « +N autres » — un mob présent dans beaucoup de biomes ne peut pas déborder sur
l'interface.

À l'ouverture d'une fiche, le mob **pousse son cri**. Le son est récupéré par réflexion sur
`getAmbientSound()` puis joué via le gestionnaire de sons du client. L'appel étant virtuel, c'est la
redéfinition du mob concerné qui s'exécute : les mobs moddés crient automatiquement juste. Repli si
la réflexion est indisponible : la convention de nommage `entity.<mob>.ambient`.

Deux approches plus simples ont été essayées puis écartées :

> **Access Transformer** sur `getAmbientSound()` — impossible. Élargir la visibilité de la méthode de
> base casse toutes les sous-classes qui la redéfinissent en `protected`, Java interdisant de réduire
> la visibilité lors d'une redéfinition. Le build échoue sur une trentaine de classes vanilla, et le
> problème serait de toute façon insoluble pour les mobs moddés.

> **`Mob.playAmbientSound()`**, publique — ne fonctionne pas côté client. Elle appelle
> `level.playSound(null, ...)`, or `ClientLevel` ne joue le son que si le joueur passé en paramètre
> est le joueur local ; avec `null`, le son est silencieusement ignoré (convention : « ce son vient
> du serveur, il arrivera par paquet »).

---

## Toast de découverte

Le toast affiche le mob **en 3D, tournant sur lui-même** (un tour toutes les 4 secondes, indexé sur
l'horloge donc indépendant du framerate). Le fond est dessiné en code, sans fichier texture.

Le rendu passe par la méthode bas niveau `renderEntityInInventory` plutôt que par la variante
« FollowsAngle », pour deux raisons :

- « FollowsAngle » borne la rotation à environ ±60° (elle passe par un arc-tangente) : un tour
  complet est impossible ;
- elle applique un découpage en coordonnées **écran absolues**, alors qu'un toast est dessiné dans un
  repère translaté — le mob serait découpé hors de sa zone, donc invisible.

---

## Configuration

Deux fichiers, générés automatiquement — le découpage n'est pas cosmétique, il suit la question
« qui décide ? ».

**`config/faunapedia-client.toml`** — options d'affichage, propres à chaque joueur :

```toml
[client]
    enableToasts = true          # afficher les toasts de découverte
    enableDiscoverySound = true  # jouer un son à la découverte
    enableMobSounds = true       # jouer le cri du mob à l'ouverture de sa fiche
```

**`config/faunapedia-server.toml`** — paramètres de détection, réglés par le serveur :

```toml
[detection]
    scanRadius = 24              # rayon de détection en blocs (4 à 128)
    scanIntervalTicks = 20       # intervalle entre deux scans (5 à 200)
```

Le scan de rencontre s'exécute côté serveur : c'est donc à lui d'en fixer les paramètres, et NeoForge
synchronise cette spec vers les clients connectés. Une config **COMMON** aurait été trompeuse — elle
n'est pas synchronisée, chaque côté lisant son propre fichier : sur serveur dédié, un joueur aurait pu
modifier `scanRadius` chez lui sans aucun effet, le scan tournant avec la valeur du serveur. Le défaut
serait resté invisible en solo, où un unique processus lit un unique fichier.

Sous NeoForge 1.21, une config SERVER est **globale** (dans `config/`) ; un monde peut la surcharger
individuellement en déposant une copie du fichier dans son dossier `serverconfig/`. Le réglage vaut donc
par défaut pour toutes les parties, tout en restant ajustable monde par monde.

---

## Succès

```
FaunaPedia (racine, 1er mob)
├── Carnet de terrain (25 %) → Naturaliste (50 %) → Encyclopédiste (75 %) → Grand recensement (100 %)
├── Face au colosse       — croiser un boss
├── Faune exotique        — croiser un mob venu d'un autre mod
└── Vieille connaissance  — croiser 100 fois le même mob
```

Les branches latérales sont volontairement indépendantes du nombre de mobs : un joueur casanier
débloque « Vieille connaissance » sans jamais atteindre 50 espèces, un explorateur fera l'inverse.
Deux styles de jeu récompensés plutôt qu'un seul.

« Faune exotique » se contente de comparer le namespace du mob à `minecraft` — aucune liste codée en
dur, le succès fonctionne avec n'importe quel mod présent ou futur.

### Paliers en pourcentage, et pourquoi un second catalogue

Les paliers sont exprimés en **pourcentage de complétion**, jamais en nombre absolu. Le vanilla
compte 82 mobs répertoriables, un modpack plusieurs centaines : un seuil fixé à 100 mobs serait tout
simplement inatteignable sans mods.

Le total de référence est calculé par `ServerMobCatalog`, **côté serveur**, indépendamment du client.
Se fier au total annoncé par ce dernier rendrait les succès falsifiables, ce que l'architecture évite
partout ailleurs. Le critère est strictement identique des deux côtés — instancier le type et tester
`instanceof Mob` — donc les deux totaux coïncident et le pourcentage affiché dans le menu est bien
celui qui débloque les succès.

### Mise en œuvre

Les succès sont décrits en datapack (`data/faunapedia/advancement/`) avec le déclencheur
`minecraft:impossible` : aucun événement vanilla ne peut les débloquer, c'est le code serveur qui
décide. La réévaluation n'a lieu que lorsque la progression change réellement — donc au plus une fois
toutes les 30 secondes par type de mob, jamais à chaque tick.

Les succès sont **rétroactifs** : un joueur ayant déjà de la progression débloque tous les paliers
acquis dès sa rencontre suivante.

---

## Compatibilité multi-mods — testée

Le mod a été validé en jeu avec les mods suivants chargés simultanément (NeoForge 1.21.1) :

| Mod | Ce qu'il éprouve |
|---|---|
| Naturalist | Beaucoup de mobs, rendus via **GeckoLib** (pipeline de rendu entièrement remplacé) |
| L_Ender's Cataclysm | Boss, mixins et access transformers |
| Aquaculture 2 | Créatures aquatiques, modèles allongés |
| GeckoLib, Curios, LionfishAPI | Dépendances |

Résultat : **184 mobs répertoriés** (82 vanilla + 102 moddés), **184 exemplaires disponibles sur
184**, et **aucun échec** de rendu, de cri, d'échantillonnage de butin ou de recherche de biomes. La
liste noire de sécurité du renderer ne s'est jamais déclenchée.

### Serveur dédié

Le mod a également été démarré sur un **serveur dédié** avec les mêmes mods : il se charge sans
erreur, et surtout **aucune classe client n'est chargée** — pas de `NoClassDefFoundError`. C'est la
validation du motif de chargement paresseux décrit dans `ModNetwork` : les gestionnaires de paquets
client ne sont référencés qu'à l'intérieur de lambdas, jamais exécutées côté serveur.

Reste non vérifié : la connexion effective d'un client à un serveur dédié, donc la circulation réelle
des paquets en multijoueur.

Vérifications visuelles également confirmées en jeu :

- les **boss de Cataclysm** sont classés dans *Boss* — le tag `c:bosses` est donc bien
  renseigné par les moddeurs, et le repli sur les seuls boss vanilla n'a pas eu à servir ;
- les **poissons d'Aquaculture** tiennent dans leur case, comme les poissons vanilla après le
  correctif d'échelle ;
- les **butins des mobs moddés** s'affichent correctement.

GeckoLib était le cas le plus à risque, puisqu'il n'utilise pas le pipeline de rendu vanilla : le
rendu bas niveau l'encaisse sans traitement particulier.

---

## Licence

MIT.
