package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Paquet serveur → client : synchronisation COMPLÈTE du FaunaPedia.
 *
 * <p>Envoyé une seule fois par session (connexion, réapparition, changement de dimension),
 * jamais à chaque tick. Les découvertes suivantes passent par {@link DiscoverEntityPayload},
 * beaucoup plus léger.</p>
 *
 * <p>Un « payload » est le format de paquet réseau moderne de Minecraft 1.20.5+ : un record
 * accompagné d'un {@link StreamCodec} qui décrit comment l'écrire/le lire sur le réseau.</p>
 */
public record SyncFaunaPediaPayload(Set<ResourceLocation> discovered) implements CustomPacketPayload {

    /** Identifiant réseau unique du paquet. */
    public static final CustomPacketPayload.Type<SyncFaunaPediaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "sync_faunapedia"));

    /** Règle d'encodage/décodage : simplement une collection de ResourceLocation. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFaunaPediaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC),
                    SyncFaunaPediaPayload::discovered,
                    SyncFaunaPediaPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
