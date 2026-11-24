package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.network.DropIndexPayload;
import com.strangevalley.faunapedia.network.RequestDropIndexPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cache client de l'index des butins (voir {@code DropIndexService} pour la construction).
 *
 * <p>Conserve les deux sens de lecture : par mob (pour la recherche du catalogue, qui demande « ce
 * mob lâche-t-il quelque chose qui corresponde ? ») et par objet (pour le survol d'inventaire, qui
 * demande « qui lâche cet objet ? »). Le serveur n'envoie que le premier ; le second est construit
 * ici, à la réception.</p>
 *
 * <p>L'index est <b>demandé à la première utilisation</b>, jamais à la connexion : la plupart des
 * parties ne s'en serviront pas, et le bâtir coûte cher au serveur.</p>
 */
public final class ClientDropIndex {

    private static Map<ResourceLocation, List<ResourceLocation>> byMob = null;
    private static Map<ResourceLocation, List<ResourceLocation>> byItem = null;
    /** Une demande est partie et sa réponse n'est pas encore arrivée. */
    private static boolean pending = false;

    /**
     * Demande l'index au serveur s'il n'est pas déjà là (ou déjà demandé).
     *
     * <p>Sans effet si le serveur n'a pas le mod : les paquets étant optionnels, le canal n'existe
     * alors pas et l'envoi serait une erreur.</p>
     */
    public static void request() {
        if (byMob != null || pending) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        RequestDropIndexPayload payload = new RequestDropIndexPayload();
        if (!minecraft.getConnection().getConnection().isConnected()
                || !minecraft.getConnection().hasChannel(payload.type())) {
            return;
        }
        pending = true;
        minecraft.getConnection().send(payload);
    }

    /** Réception de l'index : on mémorise les deux sens de lecture. */
    public static void accept(DropIndexPayload payload) {
        Map<ResourceLocation, List<ResourceLocation>> mobs = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> items = new HashMap<>();

        for (DropIndexPayload.MobDrops entry : payload.entries()) {
            mobs.put(entry.entityId(), List.copyOf(entry.itemIds()));
            for (ResourceLocation itemId : entry.itemIds()) {
                items.computeIfAbsent(itemId, key -> new ArrayList<>()).add(entry.entityId());
            }
        }

        byMob = mobs;
        byItem = items;
        pending = false;
    }

    /**
     * Invalide l'index. Appelé à chaque nouvelle découverte : le serveur filtre l'index aux mobs
     * découverts, donc un mob de plus signifie un index périmé.
     */
    public static void invalidate() {
        byMob = null;
        byItem = null;
        pending = false;
    }

    /** L'index est-il disponible ? Faux tant que la réponse du serveur n'est pas arrivée. */
    public static boolean isReady() {
        return byMob != null;
    }

    /** Mobs découverts lâchant cet objet. Liste vide si l'index n'est pas encore là. */
    public static List<ResourceLocation> mobsDropping(ResourceLocation itemId) {
        if (byItem == null) {
            return List.of();
        }
        return Collections.unmodifiableList(byItem.getOrDefault(itemId, List.of()));
    }

    /** Objets que ce mob peut lâcher. Liste vide si l'index n'est pas encore là. */
    public static List<ResourceLocation> dropsOf(ResourceLocation entityId) {
        if (byMob == null) {
            return List.of();
        }
        return byMob.getOrDefault(entityId, List.of());
    }

    private ClientDropIndex() {}
}
