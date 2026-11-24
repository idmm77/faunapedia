package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Paquet client → serveur : le joueur ouvre la fiche détaillée d'un mob.
 *
 * <p>C'est le seul paquet montant du mod. Le serveur <b>vérifie</b> que le joueur a bien découvert ce
 * mob avant de répondre : un client modifié qui demanderait la fiche d'un mob jamais rencontré
 * n'obtiendrait rien. Le verrouillage n'est donc pas seulement visuel.</p>
 */
public record RequestMobInfoPayload(ResourceLocation entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestMobInfoPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "request_mob_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMobInfoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    RequestMobInfoPayload::entityId,
                    RequestMobInfoPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
