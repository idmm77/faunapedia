package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.FaunaPediaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Method;

/**
 * Joue le cri d'un mob à l'ouverture de sa fiche.
 *
 * <h2>Pourquoi ce n'est pas trivial</h2>
 *
 * <p>{@code getAmbientSound()} est {@code protected} dans {@code Mob}, et deux approches plus
 * simples ont été essayées puis écartées :</p>
 *
 * <ul>
 *   <li><b>Access Transformer</b> (mécanisme NeoForge d'élargissement de visibilité) : impossible.
 *       Rendre la méthode de base publique casse toutes les sous-classes qui la redéfinissent en
 *       {@code protected}, Java interdisant de réduire la visibilité lors d'une redéfinition. Le
 *       build échoue sur une trentaine de classes vanilla, et le problème serait de toute façon
 *       insoluble pour les mobs moddés.</li>
 *   <li><b>{@code Mob.playAmbientSound()}</b>, publique : ne fonctionne pas côté client. Elle finit
 *       par appeler {@code level.playSound(null, ...)}, or {@code ClientLevel} ne joue le son que si
 *       le joueur passé en paramètre est le joueur local. Avec {@code null}, le son est ignoré — la
 *       convention étant « ce son vient du serveur, il arrivera par paquet ».</li>
 * </ul>
 *
 * <p>On récupère donc le son par réflexion, puis on le joue nous-mêmes via le gestionnaire de sons
 * du client. L'appel étant virtuel, c'est bien la redéfinition du mob concerné qui s'exécute : les
 * mobs ajoutés par d'autres mods émettent automatiquement leur propre cri.</p>
 *
 * <p>Tous les mobs n'ont pas de son d'ambiance (le creeper, par exemple, n'en a pas) : dans ce cas
 * rien n'est joué, ce qui est le comportement vanilla.</p>
 */
public final class MobSoundPlayer {

    private static Method ambientSoundMethod;
    private static boolean resolutionAttempted = false;

    public static void playFor(EntityType<?> type) {
        if (!FaunaPediaConfig.ENABLE_MOB_SOUNDS.get()) {
            return;
        }

        LivingEntity sample = MobSampleCache.get(type);
        if (!(sample instanceof Mob mob)) {
            return;
        }

        SoundEvent sound = ambientSoundOf(mob);
        if (sound == null) {
            // Repli : convention de nommage des sons vanilla (entity.<mob>.ambient).
            sound = soundByNamingConvention(type);
        }
        if (sound == null) {
            return;
        }

        // forUI : son joué à plat, sans position ni atténuation par distance — donc toujours audible.
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.8F));
    }

    /** Appelle {@code getAmbientSound()} par réflexion. Renvoie {@code null} en cas d'échec. */
    private static SoundEvent ambientSoundOf(Mob mob) {
        Method method = resolveMethod();
        if (method == null) {
            return null;
        }
        try {
            return (SoundEvent) method.invoke(mob);
        } catch (Throwable t) {
            // Échec propre à ce mob (mod capricieux sur un exemplaire hors monde) : on n'invalide
            // surtout pas la méthode pour tous les autres.
            FaunaPedia.LOGGER.debug("[FaunaPedia] Cri indisponible pour {} : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), t.toString());
            return null;
        }
    }

    /** Résolution paresseuse, tentée une seule fois puis mise en cache (succès comme échec). */
    private static Method resolveMethod() {
        if (resolutionAttempted) {
            return ambientSoundMethod;
        }
        resolutionAttempted = true;
        try {
            Method method = Mob.class.getDeclaredMethod("getAmbientSound");
            method.setAccessible(true);
            ambientSoundMethod = method;
        } catch (Throwable t) {
            ambientSoundMethod = null;
            FaunaPedia.LOGGER.warn("[FaunaPedia] Accès au cri des mobs impossible, repli sur la "
                    + "convention de nommage des sons. Cause : {}", t.toString());
        }
        return ambientSoundMethod;
    }

    /**
     * Repli sans réflexion : les sons vanilla suivent le motif {@code entity.<mob>.ambient}.
     * Couvre la plupart des mobs vanilla et les mods qui respectent la convention.
     */
    private static SoundEvent soundByNamingConvention(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            return null;
        }
        ResourceLocation soundId =
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "entity." + id.getPath() + ".ambient");
        return BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
    }

    private MobSoundPlayer() {}
}
