package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPedia;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Dessine l'icône d'un mob en effectuant un vrai rendu 3D de l'entité.
 *
 * <p>C'est ce qui permet aux mobs ajoutés par d'autres mods d'avoir eux aussi leur icône : on ne
 * fournit aucune image, c'est le <i>renderer</i> du mob (celui que le mod a lui-même enregistré) qui
 * est appelé. Aucune texture n'est donc à livrer avec FaunaPedia.</p>
 *
 * <p><b>Sécurité :</b> certains renderers de mods supposent que l'entité est réellement présente
 * dans le monde et peuvent lever une exception dans notre contexte. Chaque rendu est donc protégé,
 * et tout type qui échoue une fois est mis en liste noire : on n'essaiera plus de le dessiner, on
 * affichera une pastille colorée à la place. Un mod mal élevé ne peut ainsi pas casser l'écran.</p>
 */
public final class EntityIconRenderer {

    /** Types dont le rendu a échoué au moins une fois : on ne réessaie plus. */
    private static final Set<EntityType<?>> BLACKLIST = new HashSet<>();

    /**
     * Dessine le mob dans un carré de {@code size} pixels dont le coin haut-gauche est
     * ({@code x}, {@code y}).
     *
     * @param angleX composante horizontale de l'angle de vue (0 = le mob regarde droit devant)
     * @return {@code true} si le rendu 3D a réussi, {@code false} si l'appelant doit se rabattre
     *         sur la pastille colorée.
     */
    public static boolean renderMob(GuiGraphics graphics, EntityType<?> type, int x, int y, int size,
                                    float angleX, float angleY) {
        if (BLACKLIST.contains(type)) {
            return false;
        }

        LivingEntity sample = MobSampleCache.get(type);
        if (sample == null) {
            return false;
        }

        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                    graphics,
                    x, y, x + size, y + size,
                    scaleFor(sample, size),
                    // Décale légèrement le mob vers le bas du cadre pour qu'il soit bien centré.
                    0.0625F,
                    angleX,
                    angleY,
                    sample);
            return true;
        } catch (Throwable t) {
            BLACKLIST.add(type);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            FaunaPedia.LOGGER.warn("[FaunaPedia] Rendu impossible pour {} : pastille de couleur utilisée "
                    + "à la place. Cause : {}", id, t.toString());
            return false;
        }
    }

    /**
     * Dessine le mob en le faisant tourner sur lui-même, sans découpage.
     *
     * <p>Utilisée par le toast de découverte. On passe ici par la méthode bas niveau de
     * {@link InventoryScreen} plutôt que par {@code renderEntityInInventoryFollowsAngle} pour deux
     * raisons :</p>
     * <ul>
     *   <li>la variante « FollowsAngle » borne la rotation à environ ±60° (elle passe par un
     *       arc-tangente), ce qui interdit un tour complet ;</li>
     *   <li>elle applique un découpage en coordonnées <b>écran absolues</b>, alors que le toast est
     *       dessiné dans un repère translaté : le mob serait découpé hors de sa zone, donc invisible.</li>
     * </ul>
     *
     * <p>Comme cette méthode bas niveau ne découpe rien, on pose nous-mêmes la découpe : beaucoup de
     * modèles débordent de leur boîte de collision (les poissons et le calmar sont rendus couchés,
     * leur longueur visible dépasse leur hitbox) et sortiraient sinon du cadre.</p>
     *
     * @param boxX coin gauche du cadre
     * @param boxY coin haut du cadre
     * @param size côté du cadre
     * @param yawDegrees angle de rotation courant, en degrés
     */
    public static boolean renderSpinningMob(GuiGraphics graphics, EntityType<?> type,
                                            int boxX, int boxY, int size, float yawDegrees) {
        if (BLACKLIST.contains(type)) {
            return false;
        }

        LivingEntity sample = MobSampleCache.get(type);
        if (sample == null) {
            return false;
        }

        int centerX = boxX + size / 2;
        int scale = scaleFor(sample, size);

        // On centre la boîte de collision dans le cadre plutôt que de poser le mob sur le bord bas.
        // Certains modèles débordent sous leur point d'origine — le calmar et ses tentacules en
        // particulier — et se retrouvaient dessinés trop bas, à moitié hors du cadre.
        int bottomY = boxY + size / 2 + (int) (sample.getBbHeight() * scale / 2.0F);

        // La découpe attend des coordonnées d'écran, alors que l'appelant (un toast) dessine dans un
        // repère translaté. On lit donc la translation courante dans la matrice pour la compenser.
        Matrix4f pose = graphics.pose().last().pose();
        int offsetX = (int) pose.m30();
        int offsetY = (int) pose.m31();

        // L'activation est volontairement HORS du try : elle doit être appariée exactement une fois
        // avec le disableScissor du finally. Si elle échouait depuis l'intérieur du try, on dépilerait
        // une découpe jamais empilée et tout le découpage de l'interface serait corrompu ensuite.
        graphics.enableScissor(offsetX + boxX, offsetY + boxY,
                offsetX + boxX + size, offsetY + boxY + size);

        try {
            // La rotation est portée par l'entité elle-même : c'est ce que lit son renderer.
            sample.setYRot(yawDegrees);
            sample.yRotO = yawDegrees;
            sample.setYBodyRot(yawDegrees);
            sample.yBodyRotO = yawDegrees;
            sample.setYHeadRot(yawDegrees);
            sample.yHeadRotO = yawDegrees;
            sample.setXRot(0.0F);
            sample.xRotO = 0.0F;

            // rotateZ(PI) remet le modèle à l'endroit : le repère de l'interface a l'axe Y inversé.
            Quaternionf uprightPose = new Quaternionf().rotateZ((float) Math.PI);

            InventoryScreen.renderEntityInInventory(
                    graphics,
                    centerX, bottomY,
                    scale,
                    new Vector3f(),
                    uprightPose,
                    null,
                    sample);
            return true;
        } catch (Throwable t) {
            BLACKLIST.add(type);
            FaunaPedia.LOGGER.warn("[FaunaPedia] Rendu tournant impossible pour {} : {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t.toString());
            return false;
        } finally {
            // Toujours refermer la découpe, y compris si le rendu a échoué : la laisser ouverte
            // masquerait tout le reste de l'interface.
            graphics.disableScissor();
        }
    }

    /**
     * Angle de rotation dérivé de l'horloge : un tour complet toutes les 4 secondes.
     * Indépendant du nombre d'images par seconde.
     */
    public static float spinAngle() {
        return (System.currentTimeMillis() % 4000L) / 4000.0F * 360.0F;
    }

    /**
     * Calcule l'échelle de rendu pour que n'importe quel mob tienne dans la case.
     *
     * <p>L'échelle est en gros « pixels par bloc » : on la déduit de la plus grande dimension de la
     * <i>hitbox</i> (boîte de collision). Un ender dragon et un poussin occupent ainsi la même place
     * à l'écran.</p>
     */
    private static int scaleFor(LivingEntity entity, int size) {
        float largestDimension = Math.max(entity.getBbWidth(), entity.getBbHeight());
        // Plancher à 1 bloc. La hitbox sous-estime souvent la taille visible du modèle : un cabillaud
        // fait 0,5 × 0,3 bloc de boîte de collision mais son modèle, rendu couché, est bien plus long.
        // Sans ce plancher, ces mobs sont dessinés énormes et débordent de leur case.
        largestDimension = Math.max(1.0F, largestDimension);
        // Marge de 20 % pour que le mob ne touche pas les bords.
        return Math.max(1, (int) (size * 0.8F / largestDimension));
    }

    /** Vide la liste noire (à la déconnexion, en même temps que le catalogue). */
    public static void reset() {
        BLACKLIST.clear();
    }

    private EntityIconRenderer() {}
}
