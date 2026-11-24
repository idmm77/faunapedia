package com.strangevalley.faunapedia.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Données de progression du FaunaPedia pour UN joueur.
 *
 * <p>À chaque type de mob rencontré est associée une {@link MobEntry} : jour de la première
 * rencontre et nombre de rencontres. L'ensemble est attaché au joueur via un Data Attachment (voir
 * {@link ModAttachments}) et sérialisé par le {@link #CODEC}, donc persistant entre déconnexions et
 * redémarrages.</p>
 *
 * <h2>Versionnage des données</h2>
 *
 * <p>Le format porte un numéro de version ({@code dataVersion}). À la lecture, {@link #migrate}
 * transforme au besoin les anciennes données vers le format courant, sans perte. Une sauvegarde
 * dépourvue du champ est traitée comme la <b>version 0</b> (format de base, antérieur au
 * versionnage). À l'écriture, on estampille toujours la {@link #CURRENT_DATA_VERSION} : les données
 * se mettent donc à niveau au premier enregistrement.</p>
 */
public class FaunaPediaData {

    /**
     * Version courante du format de données.
     *
     * <p>Historique :</p>
     * <ul>
     *   <li><b>0</b> — format de base : aucun champ {@code dataVersion} présent (toutes les
     *       sauvegardes créées avant l'introduction du versionnage).</li>
     *   <li><b>1</b> — introduction du champ de version. La structure des entrées est inchangée.</li>
     *   <li><b>2</b> — ajout du lieu de première rencontre ({@code firstSeenPos},
     *       {@code firstSeenBiome}) dans chaque entrée.</li>
     * </ul>
     */
    public static final int CURRENT_DATA_VERSION = 2;

    /**
     * Délai minimum entre deux rencontres comptabilisées pour un même type, en ticks (30 secondes).
     *
     * <p>Sans ce délai, rester à côté d'une vache ferait grimper le compteur d'un point par seconde.
     * On veut compter des « rencontres », pas des secondes de présence.</p>
     */
    public static final long ENCOUNTER_COOLDOWN_TICKS = 600L;

    /** Résultat de l'enregistrement d'une rencontre, pour que l'appelant sache quoi faire. */
    public enum EncounterResult {
        /** Mob jamais vu : il faut notifier le client. */
        DISCOVERED,
        /** Mob déjà connu, compteur incrémenté : il faut sauvegarder. */
        COUNTED,
        /** Mob déjà connu mais revu trop tôt : rien à faire. */
        UNCHANGED
    }

    public static final Codec<FaunaPediaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Version stockée. Absente = 0 (format de base). Le getter écrit toujours la version
            // courante : les données se mettent ainsi à niveau dès le prochain enregistrement.
            Codec.INT.optionalFieldOf("dataVersion", 0).forGetter(data -> CURRENT_DATA_VERSION),
            Codec.unboundedMap(ResourceLocation.CODEC, MobEntry.CODEC)
                    .optionalFieldOf("entries", Map.of())
                    .forGetter(data -> data.entries)
    ).apply(instance, FaunaPediaData::fromSerialized));

    private final Map<ResourceLocation, MobEntry> entries = new HashMap<>();

    /** Constructeur par défaut (données vides) utilisé par le Data Attachment. */
    public FaunaPediaData() {
    }

    /** Constructeur de désérialisation utilisé par le codec, une fois la migration appliquée. */
    public FaunaPediaData(Map<ResourceLocation, MobEntry> entries) {
        this.entries.putAll(entries);
    }

    /**
     * Point de désérialisation : applique la migration puis construit l'objet.
     *
     * @param storedVersion version lue dans la sauvegarde (0 si le champ était absent)
     * @param entries       entrées telles que lues sur le disque
     */
    private static FaunaPediaData fromSerialized(int storedVersion,
                                                 Map<ResourceLocation, MobEntry> entries) {
        return new FaunaPediaData(migrate(storedVersion, entries));
    }

    /**
     * Point de migration des anciennes données vers le format courant.
     *
     * <p>Aucune transformation n'est nécessaire aujourd'hui (le passage de 0 à 1 n'a fait qu'ajouter
     * le champ de version, sans toucher aux entrées), mais le point d'extension est en place : les
     * migrations futures s'insèrent ici, chacune gardée par son palier de version, en enchaînant du
     * plus ancien au plus récent. La progression est toujours conservée.</p>
     */
    private static Map<ResourceLocation, MobEntry> migrate(int storedVersion,
                                                           Map<ResourceLocation, MobEntry> entries) {
        if (storedVersion >= CURRENT_DATA_VERSION) {
            return entries; // déjà au format courant, rien à faire
        }

        // Copie modifiable : les migrations futures transformeront ce contenu.
        Map<ResourceLocation, MobEntry> result = new HashMap<>(entries);

        // --- Migration 0 -> 1 -------------------------------------------------------------------
        // Rien à transformer : seul le champ de version a été ajouté, les entrées sont conservées.

        // --- Migration 1 -> 2 -------------------------------------------------------------------
        // Rien à transformer non plus : le lieu de première rencontre a été ajouté en champ
        // FACULTATIF, si bien qu'une entrée ancienne se relit telle quelle avec un lieu vide. Les
        // mobs déjà découverts n'auront donc pas de lieu — on ne peut pas l'inventer après coup —
        // mais aucune progression n'est perdue, et les rencontres suivantes en enregistreront un.

        FaunaPedia.LOGGER.debug("[FaunaPedia] Données migrées de la version {} vers {} "
                + "({} entrées préservées).", storedVersion, CURRENT_DATA_VERSION, result.size());
        return result;
    }

    /**
     * Enregistre une rencontre avec un type de mob.
     *
     * @param day      jour Minecraft courant
     * @param gameTime tick de jeu courant
     * @param where    dimension et position du mob, mémorisées uniquement à la première rencontre
     * @param biome    biome du mob, mémorisé dans les mêmes conditions
     */
    public EncounterResult recordEncounter(ResourceLocation id, long day, long gameTime,
                                           GlobalPos where, ResourceLocation biome) {
        MobEntry existing = entries.get(id);

        if (existing == null) {
            entries.put(id, MobEntry.first(day, gameTime, where, biome));
            return EncounterResult.DISCOVERED;
        }

        if (gameTime - existing.lastCountedTick() >= ENCOUNTER_COOLDOWN_TICKS) {
            entries.put(id, existing.countAgain(gameTime));
            return EncounterResult.COUNTED;
        }

        return EncounterResult.UNCHANGED;
    }

    public boolean isDiscovered(ResourceLocation id) {
        return entries.containsKey(id);
    }

    /** Entrée détaillée d'un mob, ou {@code null} s'il n'a jamais été rencontré. */
    public MobEntry getEntry(ResourceLocation id) {
        return entries.get(id);
    }

    /** Identifiants de tous les mobs découverts (c'est ce que le client reçoit pour sa liste). */
    public Set<ResourceLocation> getDiscovered() {
        return Collections.unmodifiableSet(entries.keySet());
    }

}
