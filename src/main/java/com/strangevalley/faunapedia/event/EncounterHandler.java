package com.strangevalley.faunapedia.event;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.FaunaPediaConfig;
import com.strangevalley.faunapedia.data.FaunaPediaData;
import com.strangevalley.faunapedia.data.MobEntry;
import com.strangevalley.faunapedia.data.ModAttachments;
import com.strangevalley.faunapedia.network.DiscoverEntityPayload;
import com.strangevalley.faunapedia.network.ModNetwork;
import com.strangevalley.faunapedia.network.SyncFaunaPediaPayload;
import com.strangevalley.faunapedia.server.FaunaPediaAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.List;

/**
 * Détection des rencontres, côté serveur uniquement.
 *
 * <p>Toutes les {@code scanIntervalTicks} ticks, on parcourt les joueurs connectés et on regarde
 * les mobs vivants dans un rayon configurable autour d'eux. Un mob compte comme « rencontré » si le
 * joueur a une ligne de vue dégagée vers lui (pas de mur entre les deux).</p>
 *
 * <p>Le scan est volontairement périodique (1 fois par seconde par défaut) et non à chaque tick,
 * pour rester très léger même avec beaucoup de joueurs.</p>
 */
@EventBusSubscriber(modid = FaunaPedia.MODID)
public final class EncounterHandler {

    /** Compteur de ticks, sert à n'exécuter le scan que toutes les N ticks. */
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < FaunaPediaConfig.SCAN_INTERVAL.get()) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        double radius = FaunaPediaConfig.SCAN_RADIUS.get();

        // On parcourt la liste des joueurs une seule fois (chacun scanné dans SA dimension), plutôt
        // que d'itérer toutes les dimensions chargées dont la plupart n'ont aucun joueur.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scanAround(player, player.serverLevel(), radius);
        }
    }

    /** Cherche les mobs visibles autour d'un joueur et enregistre les nouveautés. */
    private static void scanAround(ServerPlayer player, ServerLevel level, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        // On ne s'intéresse qu'aux Mob : cela exclut les projectiles, items, bateaux, porte-armures…
        // tout en incluant automatiquement les mobs ajoutés par d'autres mods (ils étendent Mob).
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, box, Mob::isAlive);
        if (nearby.isEmpty()) {
            return;
        }

        FaunaPediaData data = player.getData(ModAttachments.FAUNAPEDIA.get());
        boolean changed = false;

        // Jour Minecraft courant et tick de jeu : le premier date la découverte, le second sert à
        // espacer les incréments du compteur de rencontres.
        long day = level.getDayTime() / 24000L;
        long gameTime = level.getGameTime();

        for (Mob mob : nearby) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (id == null) {
                continue;
            }

            // Optimisation : si ce type a déjà été comptabilisé il y a moins du délai, une nouvelle
            // rencontre serait UNCHANGED. On évite alors le raycast de ligne de vue — l'opération la
            // plus coûteuse du scan — puisqu'il ne changerait rien. En régime permanent (on est
            // presque toujours près de mobs déjà vus récemment), cela supprime l'essentiel des
            // raycasts. Le compteur continue de monter une fois le délai écoulé, donc le succès
            // « Vieille connaissance » reste atteignable.
            MobEntry entry = data.getEntry(id);
            if (entry != null && gameTime - entry.lastCountedTick() < FaunaPediaData.ENCOUNTER_COOLDOWN_TICKS) {
                continue;
            }

            // « Portée de vue » : rayon dégagé entre les yeux du joueur et le mob.
            if (!player.hasLineOfSight(mob)) {
                continue;
            }

            // Lieu de la rencontre : celui du MOB, pas du joueur — c'est là que l'espèce vit, et
            // c'est ce qui rend le biome mémorisé pertinent. Ces valeurs ne sont retenues qu'à la
            // toute première rencontre (voir MobEntry.countAgain, qui les conserve telles quelles).
            BlockPos mobPos = mob.blockPosition();
            GlobalPos where = GlobalPos.of(level.dimension(), mobPos);
            ResourceLocation biome = level.getBiome(mobPos).unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);

            FaunaPediaData.EncounterResult result = data.recordEncounter(id, day, gameTime, where, biome);
            if (result == FaunaPediaData.EncounterResult.UNCHANGED) {
                continue; // ne devrait pas arriver après le filtre ci-dessus ; gardé par sécurité
            }

            changed = true;
            if (result == FaunaPediaData.EncounterResult.DISCOVERED) {
                // Paquet incrémental : uniquement le mob nouvellement découvert.
                ModNetwork.sendToClient(player, new DiscoverEntityPayload(id));
            }
            // Si le compteur a seulement été incrémenté, rien n'est envoyé : le client lira la
            // valeur à jour en ouvrant la fiche, ce qui évite tout trafic périodique.

            // Les succès ne sont réévalués que sur changement réel.
            FaunaPediaAdvancements.check(player, data, id, mob.getType());
        }

        if (changed) {
            // Marque l'attachment comme modifié afin qu'il soit bien resauvegardé.
            player.setData(ModAttachments.FAUNAPEDIA.get(), data);
        }
    }

    /**
     * Synchronisation complète, envoyée <b>une seule fois par connexion</b>.
     *
     * <p>C'est le seul moment où l'ensemble complet est transmis. Ensuite, chaque découverte n'envoie
     * que son identifiant ({@link DiscoverEntityPayload}), jamais le set entier.</p>
     *
     * <p>On ne resynchronise volontairement PAS à la réapparition ni au changement de dimension :
     * le cache client ({@code ClientFaunaPediaState}) est statique et survit à ces événements
     * (seule une vraie déconnexion le vide), donc un renvoi complet y serait redondant.</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FaunaPediaData data = player.getData(ModAttachments.FAUNAPEDIA.get());
            ModNetwork.sendToClient(player,
                    new SyncFaunaPediaPayload(new HashSet<>(data.getDiscovered())));
        }
    }

    private EncounterHandler() {}
}
