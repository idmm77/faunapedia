package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPediaConfig;
import com.strangevalley.faunapedia.client.toast.DiscoveryToast;
import com.strangevalley.faunapedia.network.DiscoverEntityPayload;
import com.strangevalley.faunapedia.network.DropIndexPayload;
import com.strangevalley.faunapedia.network.MobInfoPayload;
import com.strangevalley.faunapedia.network.SyncFaunaPediaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;

/**
 * Traitement des paquets reçus du serveur — CLASSE EXCLUSIVEMENT CLIENT.
 *
 * <p>Elle n'est jamais chargée sur un serveur dédié (voir la note dans
 * {@code ModNetwork}), car elle touche à {@link Minecraft} et à l'interface graphique.</p>
 */
public final class ClientPayloadHandler {

    /**
     * Synchronisation complète (une fois par connexion) : on remplace tout le cache local.
     *
     * <p>Idempotent : rejouer le même paquet aboutit exactement au même état.</p>
     */
    public static void handleSync(SyncFaunaPediaPayload payload) {
        ClientFaunaPediaState.setAll(payload.discovered());
    }

    /**
     * Delta d'une nouvelle découverte : mise à jour du cache, toast et son.
     *
     * <p>Idempotent : {@code add} renvoie {@code false} si le mob était déjà connu, auquel cas on
     * sort sans rejouer le toast ni le son. Recevoir deux fois le même delta est donc sans effet.</p>
     */
    public static void handleDiscover(DiscoverEntityPayload payload) {
        ResourceLocation id = payload.entityId();

        // Application idempotente : rien à faire si le mob était déjà dans le cache.
        if (!ClientFaunaPediaState.add(id)) {
            return;
        }

        // Badge « nouveau » dans le menu, jusqu'à ce que le joueur ouvre la fiche du mob.
        ClientFaunaPediaState.markUnseen(id);

        // L'index des butins est filtré aux mobs découverts : un mob de plus le rend périmé.
        ClientDropIndex.invalidate();

        Minecraft minecraft = Minecraft.getInstance();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);

        if (type != null && FaunaPediaConfig.ENABLE_TOASTS.get()) {
            minecraft.getToasts().addToast(new DiscoveryToast(type, id));
        }

        if (FaunaPediaConfig.ENABLE_SOUND.get()) {
            // Son de découverte distinct de celui des succès vanilla.
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.6F));
        }
    }

    /** Fiche détaillée reçue : on la met en cache, l'écran de détail l'affichera au prochain rendu. */
    public static void handleMobInfo(MobInfoPayload payload) {
        ClientMobInfoCache.store(payload);
    }

    /** Index des butins reçu (déjà restreint par le serveur aux mobs découverts par ce joueur). */
    public static void handleDropIndex(DropIndexPayload payload) {
        ClientDropIndex.accept(payload);
    }

    private ClientPayloadHandler() {}
}
