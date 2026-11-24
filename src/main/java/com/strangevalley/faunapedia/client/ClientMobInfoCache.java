package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.network.MobInfoPayload;
import com.strangevalley.faunapedia.network.RequestMobInfoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cache client des fiches détaillées reçues du serveur.
 *
 * <p>Une fiche n'est demandée qu'une seule fois par session : une fois reçue, rouvrir la fiche du
 * même mob est instantané et ne génère aucun trafic réseau.</p>
 */
public final class ClientMobInfoCache {

    private static final Map<ResourceLocation, MobInfoPayload> CACHE = new HashMap<>();

    /** Demandes déjà envoyées, pour ne pas spammer le serveur pendant que la réponse arrive. */
    private static final Set<ResourceLocation> PENDING = new HashSet<>();

    /** Fiche déjà reçue, ou {@code null} si elle n'est pas encore disponible. */
    public static MobInfoPayload get(ResourceLocation entityId) {
        return CACHE.get(entityId);
    }

    /** Demande la fiche au serveur si elle n'est ni en cache ni déjà demandée. */
    public static void request(ResourceLocation entityId) {
        if (CACHE.containsKey(entityId) || PENDING.contains(entityId)) {
            return;
        }
        // Ne demande rien si le serveur ne connaît pas ce canal (serveur sans le mod, paquets
        // optionnels) : cela éviterait une erreur, et la réponse n'arriverait de toute façon jamais.
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(RequestMobInfoPayload.TYPE)) {
            return;
        }
        PENDING.add(entityId);
        PacketDistributor.sendToServer(new RequestMobInfoPayload(entityId));
    }

    public static void store(MobInfoPayload payload) {
        CACHE.put(payload.entityId(), payload);
        PENDING.remove(payload.entityId());
    }

    /** Vidé à la déconnexion : les biomes et butins dépendent du monde chargé. */
    public static void clear() {
        CACHE.clear();
        PENDING.clear();
    }

    private ClientMobInfoCache() {}
}
