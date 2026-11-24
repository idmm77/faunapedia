package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.MobCategories;
import com.strangevalley.faunapedia.MobCategories.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue client de TOUS les types de mobs présents dans l'instance de jeu.
 *
 * <p>Entièrement générique : la liste est construite en parcourant le registre {@code EntityType}
 * au chargement. Aucun mob n'est codé en dur, donc les mobs ajoutés par n'importe quel autre mod
 * chargé apparaissent automatiquement. La classification elle-même est déléguée à
 * {@link MobCategories} (commune au client et au serveur).</p>
 *
 * <p>Les exemplaires nécessaires au <b>rendu</b> des icônes sont gérés à part, paresseusement et
 * uniquement pour les mobs visibles, par {@link MobSampleCache} — ce catalogue ne conserve donc
 * aucune entité vivante.</p>
 */
public final class EntityCatalog {

    /** Cache : type de mob → catégorie. {@code null} tant que le catalogue n'a pas été construit. */
    private static Map<EntityType<?>, Category> categories = null;

    /** Cache : catégorie → liste des types. */
    private static Map<Category, List<EntityType<?>>> byCategory = null;

    /**
     * Construit le catalogue si ce n'est pas déjà fait.
     *
     * <p>Nécessite un monde client chargé (pour pouvoir instancier les entités lors de la
     * classification). Si aucun monde n'est disponible, on ne construit rien et on réessaiera.</p>
     */
    public static void ensureBuilt() {
        if (categories != null) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Map<EntityType<?>, Category> result = new LinkedHashMap<>();
        int ignored = 0;

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Category category = MobCategories.classify(type, level);
            if (category != null) {
                result.put(type, category);
            } else {
                ignored++;
            }
        }

        categories = result;
        byCategory = regroup(result);

        FaunaPedia.LOGGER.info("[FaunaPedia] Catalogue construit : {} mobs répertoriés "
                + "({} types ignorés car non-mobs).", result.size(), ignored);
    }

    /**
     * Vide le cache. Appelé à la déconnexion : la classification dépend du jeu de mods chargé,
     * qui peut différer dans une autre partie.
     */
    public static void reset() {
        categories = null;
        byCategory = null;
    }

    private static Map<Category, List<EntityType<?>>> regroup(Map<EntityType<?>, Category> source) {
        Map<Category, List<EntityType<?>>> map = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            map.put(category, new ArrayList<>());
        }
        source.forEach((type, category) -> map.get(category).add(type));
        return map;
    }

    // --- Accès public --------------------------------------------------------------------------

    /** Tous les types de mobs répertoriés, groupés par catégorie. Vide si non encore construit. */
    public static Map<Category, List<EntityType<?>>> getByCategory() {
        return byCategory == null ? Collections.emptyMap() : byCategory;
    }

    public static Category categoryOf(EntityType<?> type) {
        if (categories == null) {
            return Category.OTHER;
        }
        return categories.getOrDefault(type, Category.OTHER);
    }

    /** Nombre total de mobs répertoriés (le « Y » du compteur X / Y). */
    public static int totalCount() {
        return categories == null ? 0 : categories.size();
    }

    /**
     * Nombre de mobs découverts <b>parmi ceux du catalogue</b>.
     *
     * <p>On filtre volontairement : si le serveur a enregistré un mob que le client n'a pas su
     * répertorier, le compteur ne doit pas pouvoir dépasser 100 %.</p>
     */
    public static int discoveredCount() {
        if (categories == null) {
            return 0;
        }
        int count = 0;
        for (EntityType<?> type : categories.keySet()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null && ClientFaunaPediaState.isDiscovered(id)) {
                count++;
            }
        }
        return count;
    }

    /** Nombre de mobs découverts dans une catégorie donnée (pour l'en-tête de section). */
    public static int discoveredCountIn(Category category) {
        int count = 0;
        for (EntityType<?> type : getByCategory().getOrDefault(category, List.of())) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null && ClientFaunaPediaState.isDiscovered(id)) {
                count++;
            }
        }
        return count;
    }

    /** Pourcentage entier (0-100), protégé contre la division par zéro. */
    public static int percentage(int done, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(100.0D * done / total);
    }

    /**
     * Couleur stable dérivée de l'identifiant du mob.
     *
     * <p>Sert de repli quand le mob n'a pas pu être dessiné, et de teinte d'icône dans le toast.</p>
     */
    public static int colorFor(ResourceLocation id) {
        int hash = id.toString().hashCode();
        // On force une luminosité correcte en gardant chaque composante dans [64, 255].
        int r = 64 + Math.abs(hash % 192);
        int g = 64 + Math.abs((hash / 192) % 192);
        int b = 64 + Math.abs((hash / 36864) % 192);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private EntityCatalog() {}
}
