package com.strangevalley.faunapedia.server;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.MobCategories;
import com.strangevalley.faunapedia.data.FaunaPediaData;
import com.strangevalley.faunapedia.data.MobEntry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * Attribution des succès du FaunaPedia.
 *
 * <p>Les succès sont décrits en datapack ({@code data/faunapedia/advancement/}) avec le déclencheur
 * {@code minecraft:impossible} : aucun événement vanilla ne peut les débloquer, c'est ce code qui
 * décide. C'est la méthode habituelle pour un critère que le jeu ne sait pas exprimer nativement,
 * et elle évite d'enregistrer un déclencheur personnalisé.</p>
 *
 * <h2>Paliers en pourcentage</h2>
 *
 * <p>Le total de référence vient de {@link ServerMobCatalog}, calculé par le serveur lui-même : se
 * fier au total annoncé par le client rendrait les succès falsifiables.</p>
 *
 * <p>Un palier en nombre absolu n'aurait pas le même sens partout — le vanilla compte 82 mobs
 * répertoriables, un modpack plusieurs centaines — au point qu'un seuil à 100 mobs serait
 * inatteignable sans mods.</p>
 */
public final class FaunaPediaAdvancements {

    /** Nom du critère, identique dans tous les fichiers de succès. */
    private static final String CRITERION = "unlocked";

    /** Paliers de complétion, en pourcentage du catalogue. */
    private static final int[] COMPLETION_MILESTONES = {25, 50, 75, 100};

    /** Nombre de rencontres avec un même mob donnant droit au succès « habitué ». */
    private static final int VETERAN_ENCOUNTERS = 100;

    /**
     * Réévalue les succès du joueur après une évolution de sa progression.
     *
     * <p>Appelée uniquement quand quelque chose a changé (découverte ou compteur incrémenté), donc
     * au plus une fois toutes les 30 secondes par type de mob — jamais à chaque tick.</p>
     *
     * @param seenId identifiant du mob qui vient d'être rencontré
     * @param seenType type correspondant
     */
    public static void check(ServerPlayer player, FaunaPediaData data,
                             ResourceLocation seenId, EntityType<?> seenType) {
        // Racine : dès le tout premier mob.
        grant(player, "root");

        // Pourcentage plutôt que nombre fixe : le vanilla compte 82 mobs répertoriables, un modpack
        // plusieurs centaines. Un seuil en nombre absolu serait inatteignable dans le premier cas.
        int discovered = data.getDiscovered().size();
        int percent = ServerMobCatalog.completionPercent(player.serverLevel(), discovered);
        for (int milestone : COMPLETION_MILESTONES) {
            if (percent >= milestone) {
                grant(player, "completion_" + milestone);
            }
        }

        // Mob venu d'un autre mod : le namespace suffit à le savoir, sans rien coder en dur.
        if (!seenId.getNamespace().equals("minecraft")) {
            grant(player, "modded");
        }

        if (isBoss(seenType)) {
            grant(player, "first_boss");
        }

        MobEntry entry = data.getEntry(seenId);
        if (entry != null && entry.encounters() >= VETERAN_ENCOUNTERS) {
            grant(player, "veteran");
        }
    }

    /**
     * Détection de boss côté serveur.
     *
     * <p>Délègue à {@link MobCategories#isBossType} : la règle « qu'est-ce qu'un boss » doit rester
     * unique, sans quoi le succès et la catégorie du menu peuvent finir par diverger.</p>
     */
    private static boolean isBoss(EntityType<?> type) {
        return MobCategories.isBossType(type);
    }

    /**
     * Accorde un succès. Sans effet s'il est déjà obtenu, donc appelable sans précaution.
     */
    private static void grant(ServerPlayer player, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, path);
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            // Succès absent : datapack désactivé par le joueur, par exemple. Rien de grave.
            return;
        }
        player.getAdvancements().award(holder, CRITERION);
    }

    private FaunaPediaAdvancements() {}
}
