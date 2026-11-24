package com.strangevalley.faunapedia.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Cache client de la progression du FaunaPedia.
 *
 * <p>Ce n'est qu'un <b>miroir</b> des données du serveur (qui reste la source de vérité) : il sert
 * uniquement à afficher le menu et les toasts sans interroger le serveur en permanence.</p>
 */
public final class ClientFaunaPediaState {

    private static final Set<ResourceLocation> DISCOVERED = new HashSet<>();

    /**
     * Mobs rencontrés pendant la session en cours et pas encore consultés : ils portent le badge
     * « nouveau » dans le menu. Volontairement <b>non persistant</b> et alimenté uniquement par les
     * deltas de découverte (jamais par le full sync de connexion), pour ne pas re-signaler comme
     * « nouveaux » des mobs déjà découverts lors d'une session précédente.
     */
    private static final Set<ResourceLocation> UNSEEN = new HashSet<>();

    /** Remplace tout le cache (paquet de synchronisation complète, une fois par connexion). */
    public static void setAll(Set<ResourceLocation> ids) {
        DISCOVERED.clear();
        DISCOVERED.addAll(ids);
        // Les découvertes reçues à la connexion ne sont pas « nouvelles ».
        UNSEEN.clear();
    }

    /** Ajoute une découverte. Renvoie {@code true} si c'était réellement une nouveauté. */
    public static boolean add(ResourceLocation id) {
        return DISCOVERED.add(id);
    }

    public static boolean isDiscovered(ResourceLocation id) {
        return DISCOVERED.contains(id);
    }

    public static Set<ResourceLocation> all() {
        return Collections.unmodifiableSet(DISCOVERED);
    }

    // --- Badge « nouveau » ----------------------------------------------------------------------

    /** Marque un mob comme fraîchement découvert (affiche le badge « nouveau »). */
    public static void markUnseen(ResourceLocation id) {
        UNSEEN.add(id);
    }

    /** Retire le badge « nouveau » (le joueur a ouvert la fiche du mob). */
    public static void markSeen(ResourceLocation id) {
        UNSEEN.remove(id);
    }

    public static boolean isUnseen(ResourceLocation id) {
        return UNSEEN.contains(id);
    }

    private ClientFaunaPediaState() {}
}
