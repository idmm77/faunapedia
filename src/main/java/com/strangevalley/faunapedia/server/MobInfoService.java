package com.strangevalley.faunapedia.server;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.data.FaunaPediaData;
import com.strangevalley.faunapedia.data.MobEntry;
import com.strangevalley.faunapedia.data.ModAttachments;
import com.strangevalley.faunapedia.network.MobInfoPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import com.strangevalley.faunapedia.network.ModNetwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construction, côté serveur, de la fiche détaillée d'un mob.
 *
 * <p>Le serveur est le seul à pouvoir produire ces données : les tables de butin et le registre des
 * biomes sont des données de monde, absentes du client.</p>
 */
public final class MobInfoService {

    /** Nombre de tirages de la table de butin. Plus il est élevé, plus les drops rares sortent. */
    private static final int LOOT_SAMPLES = 80;
    private static final int MAX_DROPS = 12;
    /**
     * Plafond très large : le client affiche ce qui tient à l'écran, et la liste complète est
     * consultable dans un écran dédié. Un plafond serré tronquerait cette liste.
     */
    private static final int MAX_BIOMES = 256;

    /**
     * Répond à une demande de fiche.
     *
     * <p><b>Contrôle d'accès :</b> si le joueur n'a jamais rencontré ce mob, on ne renvoie rien du
     * tout. C'est ce qui rend le verrouillage réel — un client modifié ne peut pas extraire les
     * informations d'un mob non débloqué.</p>
     */
    public static void respond(ServerPlayer player, ResourceLocation entityId) {
        FaunaPediaData data = player.getData(ModAttachments.FAUNAPEDIA.get());
        MobEntry entry = data.getEntry(entityId);
        // Contrôle d'accès : pas d'entrée = mob jamais rencontré = aucune information renvoyée.
        if (entry == null) {
            return;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (type == null) {
            return;
        }

        ServerLevel level = player.serverLevel();

        Entity sample;
        try {
            sample = type.create(level);
        } catch (Throwable t) {
            FaunaPedia.LOGGER.debug("[FaunaPedia] Fiche impossible pour {} : instanciation échouée.", entityId);
            return;
        }
        if (!(sample instanceof LivingEntity living)) {
            return;
        }

        try {
            MobInfoPayload payload = new MobInfoPayload(
                    entityId,
                    living.getMaxHealth(),
                    living.getArmorValue(),
                    attributeValue(living, Attributes.ATTACK_DAMAGE),
                    attributeValue(living, Attributes.MOVEMENT_SPEED),
                    living.getBbWidth(),
                    living.getBbHeight(),
                    sampleDrops(level, player, living, type),
                    findSpawnBiomes(level, type),
                    entry.firstSeenDay(),
                    entry.encounters(),
                    entry.firstSeenPos(),
                    entry.firstSeenBiome());

            ModNetwork.sendToClient(player, payload);
        } finally {
            // L'exemplaire n'a jamais été ajouté au monde ; on le neutralise par précaution.
            try {
                living.discard();
            } catch (Throwable ignored) {
                // Sans conséquence.
            }
        }
    }

    /** Lit un attribut s'il existe sur ce mob (tous les mobs n'ont pas de dégâts d'attaque). */
    private static float attributeValue(LivingEntity living, Holder<Attribute> attribute) {
        try {
            if (living.getAttributes().hasAttribute(attribute)) {
                return (float) living.getAttributeValue(attribute);
            }
        } catch (Throwable ignored) {
            // Attribut absent ou mod capricieux : on renvoie 0, la fiche masquera la ligne.
        }
        return 0.0F;
    }

    /**
     * Estime les butins possibles en tirant plusieurs fois la table de loot du mob.
     *
     * <p>On ne peut pas « lire » une table de butin de façon fiable (les entrées sont des conditions
     * imbriquées, souvent personnalisées par les mods). L'échantillonnage donne un résultat correct
     * et fonctionne pour n'importe quelle table, y compris moddée. Pour chaque objet, on retient la
     * quantité maximale observée.</p>
     */
    private static List<ItemStack> sampleDrops(ServerLevel level, ServerPlayer player,
                                               LivingEntity living, EntityType<?> type) {
        Map<Item, Integer> maxCounts = new LinkedHashMap<>();
        try {
            ResourceKey<LootTable> lootKey = type.getDefaultLootTable();
            LootTable table = level.getServer().reloadableRegistries().getLootTable(lootKey);
            if (table == LootTable.EMPTY) {
                return List.of();
            }

            // On simule une mise à mort PAR LE JOUEUR, et pas des dégâts génériques.
            // Beaucoup de tables de butin portent une condition « killed_by_player » : le blaze ne
            // lâche ses bâtons que dans ce cas. Sans joueur dans le contexte, la condition échoue et
            // le butin ressort vide. C'est de toute façon le scénario qui intéresse le lecteur de la
            // fiche : « qu'est-ce que je récupère si je le tue ? ».
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, living)
                    .withParameter(LootContextParams.ORIGIN, living.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .create(LootContextParamSets.ENTITY);

            for (int i = 0; i < LOOT_SAMPLES; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (!stack.isEmpty()) {
                        maxCounts.merge(stack.getItem(), stack.getCount(), Math::max);
                    }
                }
            }
        } catch (Throwable t) {
            FaunaPedia.LOGGER.debug("[FaunaPedia] Échantillonnage du butin impossible pour {} : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t.toString());
            return List.of();
        }

        List<ItemStack> drops = new ArrayList<>(codeOnlyDrops(type));
        for (Map.Entry<Item, Integer> entry : maxCounts.entrySet()) {
            if (drops.size() >= MAX_DROPS) {
                break;
            }
            drops.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        return drops;
    }

    /**
     * Butins lâchés par du CODE et absents de toute table de butin.
     *
     * <p>C'est la seule exception codée en dur du mod, et elle est assumée. Certains mobs lâchent
     * leurs objets depuis {@code dropCustomDeathLoot()} plutôt que depuis une table de données :
     * aucun échantillonnage ne peut alors les découvrir, et les observer exigerait de tuer
     * réellement le mob — avec toutes les conséquences que cela aurait sur le monde.</p>
     *
     * <p><b>Limite connue :</b> cette liste ne couvre que le vanilla. Un boss moddé lâchant son butin
     * par code n'apparaîtra pas ici, et sa fiche restera silencieusement incomplète. Sept mobs
     * vanilla utilisent ce mécanisme, mais pour la plupart dans des cas marginaux (disque du creeper
     * tué par un squelette, bloc porté par l'enderman, équipement des zombies et squelettes) ; seul
     * le Wither a un butin emblématique qui mérite de figurer sur sa fiche.</p>
     */
    static List<ItemStack> codeOnlyDrops(EntityType<?> type) {
        if (type == EntityType.WITHER) {
            return List.of(new ItemStack(Items.NETHER_STAR));
        }
        return List.of();
    }

    /**
     * Cherche dans quels biomes ce mob apparaît naturellement.
     *
     * <p>On parcourt le registre des biomes du monde chargé : la méthode marche donc aussi pour les
     * biomes et les mobs ajoutés par d'autres mods. Attention, cela ne couvre que les apparitions
     * <b>naturelles par biome</b> : les mobs liés à une structure (squelette wither dans une
     * forteresse, par exemple) ne remontent pas ici.</p>
     */
    private static List<ResourceLocation> findSpawnBiomes(ServerLevel level, EntityType<?> type) {
        List<ResourceLocation> result = new ArrayList<>();
        try {
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);

            for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomes.entrySet()) {
                MobSpawnSettings settings = entry.getValue().getMobSettings();

                // On balaie toutes les catégories plutôt que la seule catégorie déclarée du mob :
                // certains mods rangent leurs mobs dans une catégorie inattendue.
                boolean spawnsHere = false;
                for (MobCategory category : MobCategory.values()) {
                    for (MobSpawnSettings.SpawnerData spawner : settings.getMobs(category).unwrap()) {
                        if (spawner.type == type) {
                            spawnsHere = true;
                            break;
                        }
                    }
                    if (spawnsHere) {
                        break;
                    }
                }

                if (spawnsHere) {
                    result.add(entry.getKey().location());
                    if (result.size() >= MAX_BIOMES) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            FaunaPedia.LOGGER.debug("[FaunaPedia] Recherche des biomes impossible pour {} : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t.toString());
        }
        return result;
    }

    private MobInfoService() {}
}
