package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Paquet serveur → client : fiche détaillée d'un mob.
 *
 * <p>Contient des informations que le client ne peut PAS calculer lui-même :</p>
 * <ul>
 *   <li>les <b>butins</b> viennent des tables de loot, qui sont des données serveur ;</li>
 *   <li>les <b>biomes d'apparition</b> viennent du registre des biomes du monde chargé.</li>
 * </ul>
 *
 * <p>Le paquet n'est envoyé qu'en réponse à une demande explicite du joueur (ouverture d'une fiche),
 * et uniquement si le mob a bien été découvert.</p>
 */
public record MobInfoPayload(
        ResourceLocation entityId,
        float maxHealth,
        float armor,
        float attackDamage,
        float movementSpeed,
        float width,
        float height,
        List<ItemStack> drops,
        List<ResourceLocation> spawnBiomes,
        long firstSeenDay,
        int encounters,
        /** Lieu de la première rencontre. Vide pour les mobs découverts avant l'ajout du champ. */
        Optional<GlobalPos> firstSeenPos,
        Optional<ResourceLocation> firstSeenBiome
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MobInfoPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "mob_info"));

    /**
     * Codec écrit à la main : le paquet a plus de champs que ne le permettent les
     * {@code StreamCodec.composite(...)} tout faits.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MobInfoPayload> STREAM_CODEC =
            StreamCodec.of(MobInfoPayload::encode, MobInfoPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MobInfoPayload payload) {
        buf.writeResourceLocation(payload.entityId());
        buf.writeFloat(payload.maxHealth());
        buf.writeFloat(payload.armor());
        buf.writeFloat(payload.attackDamage());
        buf.writeFloat(payload.movementSpeed());
        buf.writeFloat(payload.width());
        buf.writeFloat(payload.height());

        buf.writeVarInt(payload.drops().size());
        for (ItemStack stack : payload.drops()) {
            ItemStack.STREAM_CODEC.encode(buf, stack);
        }

        buf.writeVarInt(payload.spawnBiomes().size());
        for (ResourceLocation biome : payload.spawnBiomes()) {
            buf.writeResourceLocation(biome);
        }

        buf.writeVarLong(payload.firstSeenDay());
        buf.writeVarInt(payload.encounters());

        // Lieu facultatif : un drapeau, puis la dimension et la position seulement s'il est présent.
        buf.writeBoolean(payload.firstSeenPos().isPresent());
        payload.firstSeenPos().ifPresent(pos -> {
            buf.writeResourceLocation(pos.dimension().location());
            buf.writeBlockPos(pos.pos());
        });
        buf.writeBoolean(payload.firstSeenBiome().isPresent());
        payload.firstSeenBiome().ifPresent(buf::writeResourceLocation);
    }

    private static MobInfoPayload decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation entityId = buf.readResourceLocation();
        float maxHealth = buf.readFloat();
        float armor = buf.readFloat();
        float attackDamage = buf.readFloat();
        float movementSpeed = buf.readFloat();
        float width = buf.readFloat();
        float height = buf.readFloat();

        int dropCount = buf.readVarInt();
        List<ItemStack> drops = new ArrayList<>(dropCount);
        for (int i = 0; i < dropCount; i++) {
            drops.add(ItemStack.STREAM_CODEC.decode(buf));
        }

        int biomeCount = buf.readVarInt();
        List<ResourceLocation> biomes = new ArrayList<>(biomeCount);
        for (int i = 0; i < biomeCount; i++) {
            biomes.add(buf.readResourceLocation());
        }

        long firstSeenDay = buf.readVarLong();
        int encounters = buf.readVarInt();

        Optional<GlobalPos> firstSeenPos = Optional.empty();
        if (buf.readBoolean()) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
            firstSeenPos = Optional.of(GlobalPos.of(dimension, buf.readBlockPos()));
        }
        Optional<ResourceLocation> firstSeenBiome = buf.readBoolean()
                ? Optional.of(buf.readResourceLocation())
                : Optional.empty();

        return new MobInfoPayload(entityId, maxHealth, armor, attackDamage, movementSpeed,
                width, height, drops, biomes, firstSeenDay, encounters,
                firstSeenPos, firstSeenBiome);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
