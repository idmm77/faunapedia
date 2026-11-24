package com.strangevalley.faunapedia.server;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.MobCategories;
import com.strangevalley.faunapedia.data.FaunaPediaData;
import com.strangevalley.faunapedia.data.ModAttachments;
import com.strangevalley.faunapedia.network.DropIndexPayload;
import com.strangevalley.faunapedia.network.ModNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Index inversé « quel mob lâche quel objet », construit côté serveur.
 *
 * <p>Répond à deux usages du client : le survol d'un objet dans l'inventaire, et la recherche par
 * butin dans le catalogue. Les deux s'appuient sur le même index — il est bâti une fois, exposé
 * deux fois.</p>
 *
 * <h2>Pourquoi côté serveur</h2>
 *
 * <p>Les tables de butin sont des données serveur, et surtout : <b>l'index est filtré aux mobs que
 * le joueur a réellement découverts</b> avant d'être envoyé. Sans ce filtrage, chercher « étoile du
 * Nether » révélerait l'existence du Wither à un joueur qui ne l'a jamais croisé — exactement le
 * verrouillage que {@link MobInfoService} applique déjà aux fiches. Filtrer côté client ne
 * protégerait rien : le client aurait quand même reçu la donnée.</p>
 *
 * <h2>Construction étalée dans le temps</h2>
 *
 * <p>Bâtir l'index instancie chaque type de mob et tire sa table de butin. Mesuré sur un serveur
 * dédié : <b>573 ms pour 184 mobs</b>, soit une dizaine de ticks perdus d'un bloc — un à-coup
 * ressenti par <i>tous</i> les joueurs connectés, pas seulement le demandeur. Et ce coût croît
 * linéairement avec la taille du modpack : un pack à 1500 mobs approcherait du seuil où le watchdog
 * de Minecraft tue le serveur en le croyant figé.</p>
 *
 * <p>La construction est donc <b>répartie sur plusieurs ticks</b>, avec un budget de
 * {@link #TICK_BUDGET_NANOS} par tick. Budget de <i>temps</i> et non nombre fixe de mobs : une table
 * de butin moddée peut coûter dix fois une table vanilla, et un quota fixe rendrait l'à-coup
 * imprévisible. Les demandeurs sont mis en attente et servis à l'achèvement — le client affiche
 * « Consultation du carnet… » pendant ce temps.</p>
 */
@EventBusSubscriber(modid = FaunaPedia.MODID)
public final class DropIndexService {

    /** Tirages par table. Moins que pour une fiche ({@code LOOT_SAMPLES}) : ici on cherche « qui
     *  lâche du cuir », un drop très rare importe moins que sur une fiche individuelle. */
    private static final int INDEX_LOOT_SAMPLES = 24;

    /** Temps maximal consacré à l'indexation par tick (8 ms, soit ~16 % d'un tick de 50 ms). */
    private static final long TICK_BUDGET_NANOS = 8_000_000L;

    /** Serveur pour lequel l'index courant a été bâti (un autre monde invalide le cache). */
    private static MinecraftServer builtFor;
    /** Index complet et prêt à servir. {@code null} tant que la construction n'est pas finie. */
    private static Map<ResourceLocation, List<ResourceLocation>> index;

    // --- État de construction (tout est null/vide hors construction) ------------------------------
    private static Deque<EntityType<?>> queue;
    private static Map<ResourceLocation, List<ResourceLocation>> partial;
    private static final Set<UUID> waiting = new LinkedHashSet<>();
    private static long startedAtMillis;

    /**
     * Sert l'index au joueur, ou le met en attente si la construction est en cours.
     */
    public static void respond(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // Changement de serveur (autre monde) : tout ce qu'on avait ne vaut plus rien.
        if (builtFor != server) {
            index = null;
            queue = null;
            partial = null;
            waiting.clear();
        }

        if (index != null) {
            send(player);
            return;
        }

        waiting.add(player.getUUID());
        if (queue == null) {
            startBuild(server);
        }
    }

    /** Prépare la file des types à traiter. Aucun travail lourd ici : tout se fait au fil des ticks. */
    private static void startBuild(MinecraftServer server) {
        queue = new ArrayDeque<>();
        BuiltInRegistries.ENTITY_TYPE.forEach(queue::add);
        partial = new LinkedHashMap<>();
        builtFor = server;
        startedAtMillis = System.currentTimeMillis();
        FaunaPedia.LOGGER.debug("[FaunaPedia] Indexation des butins démarrée ({} types à examiner).",
                queue.size());
    }

    /**
     * Avance la construction d'un tick, dans la limite du budget.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (queue == null) {
            return;
        }

        // Le tirage d'une table de butin simule une mort PAR UN JOUEUR : il en faut donc un. Sans
        // joueur connecté, on met la construction en pause plutôt que de produire un index faussé
        // où tous les butins conditionnés par « killed_by_player » manqueraient.
        ServerPlayer context = event.getServer().getPlayerList().getPlayers().stream()
                .findFirst().orElse(null);
        if (context == null) {
            return;
        }

        ServerLevel level = context.serverLevel();
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;

        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            EntityType<?> type = queue.poll();
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null || !MobCategories.isTrackableMob(type, level)) {
                continue;
            }
            List<ResourceLocation> items = dropsOf(level, context, type);
            if (!items.isEmpty()) {
                partial.put(id, items);
            }
        }

        if (queue.isEmpty()) {
            finish(event.getServer());
        }
    }

    /** Publie l'index terminé et sert tous ceux qui l'attendaient. */
    private static void finish(MinecraftServer server) {
        index = partial;
        partial = null;
        queue = null;

        FaunaPedia.LOGGER.info("[FaunaPedia] Index des butins construit : {} mobs indexés en {} ms "
                + "(étalé sur plusieurs ticks).", index.size(), System.currentTimeMillis() - startedAtMillis);

        for (UUID uuid : waiting) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            // Un joueur parti entre-temps est simplement ignoré : il redemandera à sa reconnexion.
            if (player != null) {
                send(player);
            }
        }
        waiting.clear();
    }

    /** Envoie l'index au joueur, restreint aux mobs qu'il a découverts. */
    private static void send(ServerPlayer player) {
        FaunaPediaData data = player.getData(ModAttachments.FAUNAPEDIA.get());

        List<DropIndexPayload.MobDrops> visible = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : index.entrySet()) {
            // Contrôle d'accès : un mob non découvert n'apparaît pas, même indirectement.
            if (data.isDiscovered(entry.getKey())) {
                visible.add(new DropIndexPayload.MobDrops(entry.getKey(), entry.getValue()));
            }
        }

        ModNetwork.sendToClient(player, new DropIndexPayload(visible));
    }

    /** Objets qu'un type de mob peut lâcher, identifiants seuls (le client résout les noms). */
    private static List<ResourceLocation> dropsOf(ServerLevel level, ServerPlayer player, EntityType<?> type) {
        Set<ResourceLocation> items = new LinkedHashSet<>();

        // Butins lâchés par du code et absents de toute table (l'étoile du Nether du Wither).
        for (ItemStack stack : MobInfoService.codeOnlyDrops(type)) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null) {
                items.add(itemId);
            }
        }

        Entity sample = null;
        try {
            sample = type.create(level);
            if (!(sample instanceof LivingEntity living)) {
                return new ArrayList<>(items);
            }

            ResourceKey<LootTable> lootKey = type.getDefaultLootTable();
            LootTable table = level.getServer().reloadableRegistries().getLootTable(lootKey);
            if (table == LootTable.EMPTY) {
                return new ArrayList<>(items);
            }

            // Même contexte que la fiche : mise à mort simulée PAR UN JOUEUR, sinon les tables
            // conditionnées par « killed_by_player » ressortent vides.
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, living)
                    .withParameter(LootContextParams.ORIGIN, living.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .create(LootContextParamSets.ENTITY);

            for (int i = 0; i < INDEX_LOOT_SAMPLES; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (!stack.isEmpty()) {
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if (itemId != null) {
                            items.add(itemId);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Un mod au constructeur ou à la table capricieuse ne doit pas empêcher d'indexer le reste.
            FaunaPedia.LOGGER.debug("[FaunaPedia] Indexation du butin impossible pour {} : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t.toString());
        } finally {
            if (sample != null) {
                try {
                    sample.discard();
                } catch (Throwable ignored) {
                    // L'exemplaire n'a jamais été ajouté au monde.
                }
            }
        }

        return new ArrayList<>(items);
    }

    private DropIndexService() {}
}
