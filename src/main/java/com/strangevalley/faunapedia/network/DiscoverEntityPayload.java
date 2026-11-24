package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Paquet serveur → client : UNE nouvelle découverte.
 *
 * <p>C'est le paquet « incrémental » : il ne transporte que l'identifiant du type de mob
 * qui vient d'être découvert, ce qui évite de renvoyer toute la collection à chaque fois.
 * Le client l'utilise pour mettre à jour son cache et afficher le toast.</p>
 */
public record DiscoverEntityPayload(ResourceLocation entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiscoverEntityPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "discover_entity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiscoverEntityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    DiscoverEntityPayload::entityId,
                    DiscoverEntityPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
