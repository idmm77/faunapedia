package com.strangevalley.faunapedia.client.toast;

import com.strangevalley.faunapedia.FaunaPediaConfig;
import com.strangevalley.faunapedia.MobCategories;
import com.strangevalley.faunapedia.MobCategories.Category;
import com.strangevalley.faunapedia.client.ColorUtil;
import com.strangevalley.faunapedia.client.EntityCatalog;
import com.strangevalley.faunapedia.client.EntityIconRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Toast affiché à la découverte d'un nouveau mob.
 *
 * <p>Un « toast » est la petite notification qui glisse en haut à droite de l'écran (comme pour les
 * succès). Plutôt que d'utiliser la texture vanilla, ce toast dessine entièrement son fond en code
 * (rectangles colorés) : il a donc une identité visuelle propre au mod <b>sans aucun fichier
 * texture à fournir</b>.</p>
 *
 * <p><b>Couleur par catégorie.</b> Le liseré, la teinte du fond et la barre de temps prennent la
 * couleur d'accent de la catégorie du mob découvert ({@link Category#getAccentColor()}) — les mêmes
 * teintes que le menu, pour qu'un rouge veuille dire « hostile » partout dans le mod, y compris
 * l'ambre des boss.</p>
 */
public class DiscoveryToast implements Toast {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;

    /** Accent de repli, si la catégorie n'a pas pu être déterminée. */
    private static final int FALLBACK_ACCENT = 0xFF6C6CF0;

    /** Base sombre du corps, avant teinte par l'accent. */
    private static final int BODY_BASE = 0xFF141420;

    private final EntityType<?> entityType;
    private final ResourceLocation entityId;
    /** Couleur d'accent, résolue une fois à la construction (voir {@link #resolveAccent}). */
    private final int accent;

    public DiscoveryToast(EntityType<?> entityType, ResourceLocation entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.accent = resolveAccent(entityType);
    }

    /**
     * Détermine l'accent du mob découvert.
     *
     * <p>On classe le type <b>directement</b> plutôt que d'interroger {@link EntityCatalog} : ce
     * dernier n'est construit qu'à la première ouverture du menu, si bien qu'un joueur découvrant un
     * mob sans avoir jamais appuyé sur la touche d'ouverture aurait vu tous ses toasts prendre la
     * couleur de repli {@code OTHER}. La classification instancie un exemplaire jetable, mais elle
     * n'a lieu qu'une fois par découverte — un événement rare, par construction.</p>
     */
    private static int resolveAccent(EntityType<?> type) {
        // Raccourci sans monde : le tag des boss se consulte sans instancier quoi que ce soit.
        if (MobCategories.isBossType(type)) {
            return Category.BOSS.getAccentColor();
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return FALLBACK_ACCENT;
        }
        Category category = MobCategories.classify(type, level);
        return category == null ? FALLBACK_ACCENT : category.getAccentColor();
    }

    @Override
    public Toast.Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        Font font = Minecraft.getInstance().font;
        long durationMs = FaunaPediaConfig.TOAST_DURATION_SECONDS.get() * 1000L;

        int accentDark = ColorUtil.mix(accent, 0xFF000000, 0.60F);

        // --- Fond du toast, dessiné en code -----------------------------------------------------
        // Corps sombre, très légèrement teinté vers l'accent : la catégorie se devine sans que le
        // texte perde son contraste.
        graphics.fill(0, 0, WIDTH, HEIGHT, ColorUtil.mix(BODY_BASE, accent, 0.14F));
        // Liseré extérieur en relief : accent en haut/à gauche, version assombrie en bas/à droite.
        graphics.fill(0, 0, WIDTH, 1, accent);                  // haut
        graphics.fill(0, HEIGHT - 1, WIDTH, HEIGHT, accentDark); // bas
        graphics.fill(0, 0, 1, HEIGHT, accent);                  // gauche
        graphics.fill(WIDTH - 1, 0, WIDTH, HEIGHT, accentDark);  // droite

        // --- Icône : le mob en 3D, tournant sur lui-même ------------------------------------------
        graphics.fill(4, 4, 28, 28, accentDark);                 // fin contour à la couleur de la catégorie
        graphics.fill(5, 5, 27, 27, 0xFF000000);                 // cadre sombre derrière le mob
        boolean rendered = EntityIconRenderer.renderSpinningMob(
                graphics, entityType,
                5, 5, 22,                            // cadre de l'icône (x, y, côté)
                EntityIconRenderer.spinAngle());
        if (!rendered) {
            // Repli si le renderer du mob échoue : pastille de couleur.
            graphics.fill(6, 6, 26, 26, EntityCatalog.colorFor(entityId));
        }

        // --- Textes -----------------------------------------------------------------------------
        graphics.drawString(font, Component.translatable("faunapedia.toast.title"), 32, 6, 0xFFFFD24B, false);
        // Nom traduit du mob (fonctionne aussi pour les mobs moddés).
        graphics.drawString(font, entityType.getDescription(), 32, 17, 0xFFFFFFFF, false);

        // --- Barre de temps restant ---------------------------------------------------------------
        // Se vide de gauche à droite : le joueur voit combien de temps il lui reste pour lire.
        float remaining = 1.0F - Math.min(1.0F, (float) timeSinceLastVisible / durationMs);
        int barWidth = Math.round((WIDTH - 2) * remaining);
        if (barWidth > 0) {
            graphics.fill(1, HEIGHT - 2, 1 + barWidth, HEIGHT - 1, accent);
        }

        return timeSinceLastVisible >= durationMs ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }

    /**
     * Le « token » sert à regrouper les toasts : en donnant un token unique par mob, deux découvertes
     * différentes s'affichent bien comme deux notifications distinctes.
     */
    @Override
    public Object getToken() {
        return entityId;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }
}
