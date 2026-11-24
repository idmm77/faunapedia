package com.strangevalley.faunapedia.server;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.MobCategories;
import com.strangevalley.faunapedia.MobCategories.Category;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalogue des mobs répertoriables, calculé <b>côté serveur</b>.
 *
 * <h2>Pourquoi un catalogue serveur distinct du client</h2>
 *
 * <p>Le client construit déjà le sien pour afficher le menu. Mais les succès et les commandes sont
 * arbitrés par le serveur ; se fier au catalogue annoncé par le client les rendrait falsifiables.
 * Le serveur calcule donc le sien, avec exactement le même classificateur commun
 * ({@link MobCategories}) — les deux catalogues coïncident donc.</p>
 *
 * <p>Le calcul instancie chaque type une fois (transitoirement) ; le résultat est mis en cache pour
 * ne pas le refaire à chaque appel.</p>
 */
public final class ServerMobCatalog {

    /** Cache : type → catégorie. {@code null} tant qu'il n'a pas été construit. */
    private static Map<EntityType<?>, Category> catalog = null;

    /** Construit le catalogue si nécessaire, et le renvoie. */
    private static Map<EntityType<?>, Category> catalog(ServerLevel level) {
        if (catalog != null) {
            return catalog;
        }
        Map<EntityType<?>, Category> result = new LinkedHashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Category category = MobCategories.classify(type, level);
            if (category != null) {
                result.put(type, category);
            }
        }
        catalog = result;
        FaunaPedia.LOGGER.info("[FaunaPedia] Catalogue serveur : {} mobs répertoriables.", result.size());
        return catalog;
    }

    /** Nombre total de mobs répertoriables (le dénominateur du pourcentage de complétion). */
    public static int totalCount(ServerLevel level) {
        return catalog(level).size();
    }

    /** Tous les types répertoriables. */
    public static Set<EntityType<?>> allTypes(ServerLevel level) {
        return Collections.unmodifiableSet(catalog(level).keySet());
    }

    /** Types répertoriables appartenant à une catégorie donnée. */
    public static List<EntityType<?>> typesInCategory(ServerLevel level, Category category) {
        List<EntityType<?>> result = new ArrayList<>();
        catalog(level).forEach((type, cat) -> {
            if (cat == category) {
                result.add(type);
            }
        });
        return result;
    }

    /**
     * Pourcentage de complétion d'un joueur, borné à 100.
     *
     * <p>Le plafond protège le cas où le serveur aurait enregistré un mob que le catalogue n'a pas su
     * répertorier : mieux vaut afficher 100 % que 103 %.</p>
     */
    public static int completionPercent(ServerLevel level, int discovered) {
        int total = totalCount(level);
        if (total <= 0) {
            return 0;
        }
        return Math.min(100, (int) (100L * discovered / total));
    }

    private ServerMobCatalog() {}
}
