package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Paquet client → serveur : demande l'index des butins.
 *
 * <p>Sans contenu — c'est une simple sollicitation. Le serveur décide seul de ce qu'il renvoie, et
 * n'y met que les mobs que ce joueur a découverts. C'est le second et dernier paquet montant du mod,
 * et comme le premier il ne transporte aucune donnée que le serveur accepterait sur parole.</p>
 */
public record RequestDropIndexPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDropIndexPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "request_drop_index"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDropIndexPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestDropIndexPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
