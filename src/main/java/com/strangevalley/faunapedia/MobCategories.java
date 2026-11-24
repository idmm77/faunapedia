package com.strangevalley.faunapedia;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/**
 * Classification des types de mobs en catégories, <b>commune aux deux côtés</b>.
 *
 * <p>Cette logique vivait à l'origine dans {@code EntityCatalog} (client). Elle a été extraite ici
 * pour que le serveur puisse l'utiliser aussi — notamment la commande « débloquer toute une
 * catégorie », qui doit ranger les mobs exactement comme le menu.</p>
 *
 * <p><b>Méthode :</b> on instancie un exemplaire du type (dans le monde fourni : client ou serveur)
 * pour tester son type Java réel ({@code instanceof}). C'est la seule façon fiable de classer aussi
 * les mobs moddés, qui héritent des classes vanilla. L'exemplaire est jeté aussitôt. Si
 * l'instanciation échoue, on retombe sur la {@link MobCategory} déclarée par le type.</p>
 */
public final class MobCategories {

    /** Catégories affichées dans le menu, dans l'ordre d'affichage. */
    public enum Category {
        HOSTILE("faunapedia.category.hostile", "hostile", 0xFFE04B4B),
        PASSIVE("faunapedia.category.passive", "passive", 0xFF6ECB63),
        AQUATIC("faunapedia.category.aquatic", "aquatic", 0xFF4BA8E0),
        FLYING("faunapedia.category.flying", "flying", 0xFFD9C24B),
        BOSS("faunapedia.category.boss", "boss", 0xFFFF9A3C),
        OTHER("faunapedia.category.other", "other", 0xFFB07BD9);

        private final String translationKey;
        private final String commandName;
        private final int accentColor;

        Category(String translationKey, String commandName, int accentColor) {
            this.translationKey = translationKey;
            this.commandName = commandName;
            this.accentColor = accentColor;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        /** Nom utilisé comme sous-commande (ex. {@code /faunapedia unlockcategory hostile}). */
        public String getCommandName() {
            return commandName;
        }

        /** Couleur d'accent de la catégorie (repère visuel dans la liste). */
        public int getAccentColor() {
            return accentColor;
        }
    }

    /**
     * Détermine la catégorie d'un type de mob, ou {@code null} si ce type n'est pas un mob
     * (item, projectile, bateau, porte-armures…) et ne doit donc pas figurer au FaunaPedia.
     *
     * @param level monde servant à instancier l'exemplaire (client ou serveur, indifféremment)
     */
    public static Category classify(EntityType<?> type, Level level) {
        Entity sample = tryCreate(type, level);

        if (sample == null) {
            // Repli sans instance : le tag des boss reste consultable, lui, sans exemplaire.
            if (isBossType(type)) {
                return Category.BOSS;
            }
            // Sinon, classification approximative via la MobCategory déclarée.
            return classifyByMobCategory(type.getCategory());
        }

        try {
            // Seuls les Mob comptent : même filtre que la détection de rencontre côté serveur,
            // pour que les compteurs restent cohérents.
            if (!(sample instanceof Mob)) {
                return null;
            }

            MobCategory mobCategory = type.getCategory();

            // Les boss passent en premier : l'ender dragon implémente Enemy et finirait sinon
            // rangé parmi les hostiles ordinaires.
            if (isBoss(type, sample)) {
                return Category.BOSS;
            }

            // Ordre de priorité : aquatique > volant > hostile > passif > autres.
            if (sample instanceof WaterAnimal || isWaterCategory(mobCategory)) {
                return Category.AQUATIC;
            }
            if (sample instanceof FlyingAnimal || sample instanceof FlyingMob
                    || sample instanceof AmbientCreature || mobCategory == MobCategory.AMBIENT) {
                return Category.FLYING;
            }
            if (sample instanceof Enemy) {
                return Category.HOSTILE;
            }
            if (sample instanceof Animal || sample instanceof AgeableMob || mobCategory == MobCategory.CREATURE) {
                return Category.PASSIVE;
            }
            return Category.OTHER;
        } finally {
            // L'exemplaire de classification est jeté aussitôt : il ne sert qu'au test instanceof.
            try {
                sample.discard();
            } catch (Throwable ignored) {
                // Sans conséquence : l'exemplaire n'a jamais été ajouté au monde.
            }
        }
    }

    /** {@code true} si le type est un mob répertoriable (a une catégorie). */
    public static boolean isTrackableMob(EntityType<?> type, Level level) {
        return classify(type, level) != null;
    }

    /**
     * Détecte un boss.
     *
     * <p>Source principale : le tag NeoForge {@code c:bosses}, auquel les mods peuvent ajouter leurs
     * propres boss — la détection reste donc générique. Repli sur les deux boss vanilla au cas où le
     * tag serait absent.</p>
     */
    private static boolean isBoss(EntityType<?> type, Entity sample) {
        if (isBossType(type)) {
            return true;
        }
        // Repli par instance : attrape aussi les sous-classes moddées d'un boss vanilla.
        return sample instanceof EnderDragon || sample instanceof WitherBoss;
    }

    /**
     * Variante <b>sans exemplaire</b> de {@link #isBoss} : n'utilise que le tag et l'identité du type.
     *
     * <p>Utile aux appelants qui n'ont pas d'entité sous la main (l'affichage, typiquement) et pour
     * qui instancier un mob serait disproportionné. Légèrement moins complète que {@code isBoss} :
     * une sous-classe moddée d'un boss vanilla absente du tag {@code c:bosses} n'est pas détectée.</p>
     */
    public static boolean isBossType(EntityType<?> type) {
        try {
            if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(Tags.EntityTypes.BOSSES)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Tag indisponible : on se contente du repli ci-dessous.
        }
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER;
    }

    /** Classification de repli, uniquement à partir de la MobCategory vanilla. */
    private static Category classifyByMobCategory(MobCategory mobCategory) {
        if (mobCategory == MobCategory.MISC) {
            return null; // presque toujours un non-mob
        }
        if (isWaterCategory(mobCategory)) {
            return Category.AQUATIC;
        }
        if (mobCategory == MobCategory.AMBIENT) {
            return Category.FLYING;
        }
        if (mobCategory == MobCategory.MONSTER) {
            return Category.HOSTILE;
        }
        if (mobCategory == MobCategory.CREATURE) {
            return Category.PASSIVE;
        }
        return Category.OTHER;
    }

    private static boolean isWaterCategory(MobCategory mobCategory) {
        return mobCategory == MobCategory.WATER_CREATURE
                || mobCategory == MobCategory.WATER_AMBIENT
                || mobCategory == MobCategory.UNDERGROUND_WATER_CREATURE
                || mobCategory == MobCategory.AXOLOTLS;
    }

    /**
     * Instancie un exemplaire. Fortement protégé : un mod tiers peut avoir un constructeur qui
     * échoue hors contexte de spawn, ce qui ne doit jamais faire planter la classification.
     */
    private static Entity tryCreate(EntityType<?> type, Level level) {
        try {
            return type.create(level);
        } catch (Throwable t) {
            FaunaPedia.LOGGER.debug("[FaunaPedia] Impossible d'instancier {} pour classification : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t.toString());
            return null;
        }
    }

    private MobCategories() {}
}
