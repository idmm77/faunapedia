package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Paquet serveur → client : index des butins, restreint aux mobs découverts par ce joueur.
 *
 * <p><b>Orienté mob</b> et non objet : l'identifiant du mob n'est écrit qu'une fois pour tous ses
 * butins, ce qui est plus compact. Le client construit lui-même la table inverse (objet → mobs) dont
 * il a besoin pour le survol d'inventaire.</p>
 *
 * <p>Seuls les <b>identifiants</b> circulent, pas des {@code ItemStack} : le client sait résoudre un
 * identifiant en objet et en nom traduit, et une pile complète coûterait bien plus cher à
 * transmettre pour rien.</p>
 */
public record DropIndexPayload(List<MobDrops> entries) implements CustomPacketPayload {

    /** Les butins d'un mob donné. */
    public record MobDrops(ResourceLocation entityId, List<ResourceLocation> itemIds) {}

    public static final CustomPacketPayload.Type<DropIndexPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "drop_index"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DropIndexPayload> STREAM_CODEC =
            StreamCodec.of(DropIndexPayload::encode, DropIndexPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, DropIndexPayload payload) {
        buf.writeVarInt(payload.entries().size());
        for (MobDrops entry : payload.entries()) {
            buf.writeResourceLocation(entry.entityId());
            buf.writeVarInt(entry.itemIds().size());
            for (ResourceLocation itemId : entry.itemIds()) {
                buf.writeResourceLocation(itemId);
            }
        }
    }

    private static DropIndexPayload decode(RegistryFriendlyByteBuf buf) {
        int mobCount = buf.readVarInt();
        List<MobDrops> entries = new ArrayList<>(mobCount);
        for (int i = 0; i < mobCount; i++) {
            ResourceLocation entityId = buf.readResourceLocation();
            int itemCount = buf.readVarInt();
            List<ResourceLocation> itemIds = new ArrayList<>(itemCount);
            for (int j = 0; j < itemCount; j++) {
                itemIds.add(buf.readResourceLocation());
            }
            entries.add(new MobDrops(entityId, itemIds));
        }
        return new DropIndexPayload(entries);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
