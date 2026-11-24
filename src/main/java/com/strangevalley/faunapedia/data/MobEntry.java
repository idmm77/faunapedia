package com.strangevalley.faunapedia.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Ce que l'on retient d'un mob donné, pour un joueur donné.
 *
 * @param firstSeenDay    jour Minecraft de la toute première rencontre
 * @param encounters      nombre de rencontres distinctes
 * @param lastCountedTick tick de jeu de la dernière rencontre comptabilisée, pour appliquer le
 *                        délai anti-répétition (voir {@link FaunaPediaData#ENCOUNTER_COOLDOWN_TICKS})
 * @param firstSeenPos    dimension et position de la première rencontre ; vide pour les entrées
 *                        créées avant l'introduction du champ
 * @param firstSeenBiome  biome de la première rencontre ; vide pour les mêmes entrées anciennes
 */
public record MobEntry(long firstSeenDay, int encounters, long lastCountedTick,
                       Optional<GlobalPos> firstSeenPos,
                       Optional<ResourceLocation> firstSeenBiome) {

    public static final Codec<MobEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("firstSeenDay", 0L).forGetter(MobEntry::firstSeenDay),
            Codec.INT.optionalFieldOf("encounters", 1).forGetter(MobEntry::encounters),
            Codec.LONG.optionalFieldOf("lastCountedTick", 0L).forGetter(MobEntry::lastCountedTick),
            // Champs facultatifs : une sauvegarde antérieure les ignore simplement, ce qui rend la
            // lecture des anciennes données possible sans transformation.
            GlobalPos.CODEC.optionalFieldOf("firstSeenPos").forGetter(MobEntry::firstSeenPos),
            ResourceLocation.CODEC.optionalFieldOf("firstSeenBiome").forGetter(MobEntry::firstSeenBiome)
    ).apply(instance, MobEntry::new));

    /** Nouvelle entrée : première rencontre, compteur à 1, lieu mémorisé. */
    public static MobEntry first(long day, long gameTime, GlobalPos where, ResourceLocation biome) {
        return new MobEntry(day, 1, gameTime, Optional.ofNullable(where), Optional.ofNullable(biome));
    }

    /** Même mob revu : compteur incrémenté, première rencontre (date et lieu) inchangée. */
    public MobEntry countAgain(long gameTime) {
        return new MobEntry(firstSeenDay, encounters + 1, gameTime, firstSeenPos, firstSeenBiome);
    }
}
