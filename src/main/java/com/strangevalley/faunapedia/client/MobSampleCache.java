package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache paresseux des exemplaires de mobs utilisés pour le rendu 3D des icônes.
 *
 * <h2>Pourquoi ce cache existe</h2>
 *
 * <p>Auparavant, {@link EntityCatalog} instanciait chaque type de mob au chargement du menu et
 * conservait <b>toutes</b> les instances vivantes en permanence (près de 200 avec quelques mods).
 * C'était du gaspillage : la plupart ne sont jamais à l'écran, et les mobs non découverts ne sont
 * jamais dessinés du tout.</p>
 *
 * <p>Ce cache applique donc un « culling » (élagage de ce qui n'est pas visible) :</p>
 * <ul>
 *   <li>un exemplaire n'est créé qu'à la <b>première demande</b>, c'est-à-dire uniquement pour une
 *       ligne réellement visible à l'écran — la boucle de rendu du menu n'appelle {@link #get} que
 *       pour les lignes visibles <b>et</b> découvertes ;</li>
 *   <li>chaque accès met à jour un horodatage ; {@link #sweep} recycle (libère) les exemplaires
 *       inactifs depuis plus de {@link #IDLE_TTL_MS} millisecondes, donc ceux sortis du viewport ;</li>
 *   <li>aucun exemplaire n'est jamais créé pour un mob non découvert, puisqu'on ne dessine pas son
 *       modèle (on affiche une case « ? »), donc {@link #get} n'est jamais appelé pour lui.</li>
 * </ul>
 *
 * <p>La classification (déterminer la catégorie d'un mob) reste dans {@link EntityCatalog} et
 * instancie, elle, chaque type une fois — mais de façon <b>transitoire</b> : l'exemplaire est jeté
 * immédiatement après le test {@code instanceof}, jamais conservé.</p>
 */
public final class MobSampleCache {

    /** Durée d'inactivité au-delà de laquelle un exemplaire est recyclé (2 secondes). */
    private static final long IDLE_TTL_MS = 2000L;

    /** Exemplaire en cache et date de son dernier accès. {@code entity} peut être {@code null}. */
    private static final class Holder {
        final LivingEntity entity;
        long lastAccessMs;

        Holder(LivingEntity entity, long now) {
            this.entity = entity;
            this.lastAccessMs = now;
        }
    }

    private static final Map<EntityType<?>, Holder> CACHE = new LinkedHashMap<>();

    /**
     * Exemplaire du mob, créé à la demande puis mis en cache.
     *
     * <p>Renvoie {@code null} si l'instanciation échoue — l'appelant retombe alors sur une pastille
     * colorée. L'échec est lui aussi mis en cache (via un {@code Holder} à entité nulle) pour ne pas
     * retenter la création à chaque image.</p>
     */
    public static LivingEntity get(EntityType<?> type) {
        long now = System.currentTimeMillis();
        Holder holder = CACHE.get(type);
        if (holder != null) {
            holder.lastAccessMs = now;
            return holder.entity;
        }
        LivingEntity created = create(type);
        CACHE.put(type, new Holder(created, now));
        return created;
    }

    /**
     * Recycle les exemplaires inactifs (sortis du viewport). Appelé à chaque tick client.
     *
     * <p>Un exemplaire visible est réaccédé à chaque image et reste donc jeune ; dès qu'une ligne
     * quitte l'écran, plus personne ne l'accède et il est libéré après le délai d'inactivité.</p>
     */
    public static void sweep() {
        if (CACHE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<EntityType<?>, Holder>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Holder holder = it.next().getValue();
            if (now - holder.lastAccessMs > IDLE_TTL_MS) {
                discardQuietly(holder.entity);
                it.remove();
            }
        }
    }

    /** Libère tout (déconnexion) : les exemplaires référencent le monde quitté. */
    public static void reset() {
        for (Holder holder : CACHE.values()) {
            discardQuietly(holder.entity);
        }
        CACHE.clear();
    }

    /**
     * Instancie un exemplaire. Fortement protégé : un renderer de mod peut échouer hors contexte de
     * monde, ce qui ne doit jamais faire planter le menu.
     */
    private static LivingEntity create(EntityType<?> type) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        try {
            Entity entity = type.create(level);
            return entity instanceof LivingEntity living ? living : null;
        } catch (Throwable t) {
            FaunaPedia.LOGGER.debug("[FaunaPedia] Exemplaire d'icône indisponible pour {} : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t.toString());
            return null;
        }
    }

    private static void discardQuietly(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            entity.discard();
        } catch (Throwable ignored) {
            // Sans conséquence : l'exemplaire n'a jamais été ajouté au monde.
        }
    }

    private MobSampleCache() {}
}
