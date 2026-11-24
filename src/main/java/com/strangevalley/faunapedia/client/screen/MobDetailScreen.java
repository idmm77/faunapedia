package com.strangevalley.faunapedia.client.screen;

import com.strangevalley.faunapedia.MobCategories.Category;
import com.strangevalley.faunapedia.client.ClientFaunaPediaState;
import com.strangevalley.faunapedia.client.ColorUtil;
import com.strangevalley.faunapedia.client.ClientMobInfoCache;
import com.strangevalley.faunapedia.client.EntityCatalog;
import com.strangevalley.faunapedia.client.EntityIconRenderer;
import com.strangevalley.faunapedia.client.MobSoundPlayer;
import com.strangevalley.faunapedia.network.MobInfoPayload;
import net.minecraft.core.GlobalPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Fiche détaillée d'un mob : statistiques, butins possibles et biomes d'apparition.
 *
 * <p>Cet écran ne s'ouvre que pour un mob déjà rencontré. Les données affichées viennent du serveur,
 * qui refuse de les fournir pour un mob non découvert — le verrouillage est donc réel et pas
 * seulement visuel.</p>
 */
public class MobDetailScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PORTRAIT_SIZE = 80;
    /** Épaisseur du cadre coloré autour du portrait 3D. */
    private static final int FRAME_THICKNESS = 2;

    private final Screen parent;
    private final EntityType<?> entityType;
    private final ResourceLocation entityId;
    private final Category category;

    /** Zone cliquable de l'en-tête « Biomes », mémorisée au rendu pour être testée au clic. */
    private int biomeHeaderTop = -1;
    private int biomeHeaderBottom = -1;

    /** Zone cliquable du dépliant [+] / [-], mémorisée au rendu de la même façon. */
    private int toggleLeft = -1;
    private int toggleRight = -1;
    private int toggleTop = -1;
    private int toggleBottom = -1;

    /**
     * Le lieu de découverte est-il déplié ? <b>Statique</b> : un joueur qui l'ouvre veut le voir sur
     * toutes les fiches, comme le tri et le filtre du catalogue qui se conservent d'une ouverture à
     * l'autre. Remis à zéro au changement de partie, avec les autres préférences d'affichage.
     */
    private static boolean showPlaceDetails = false;

    /** Appelé au changement de partie, avec les autres préférences d'affichage. */
    public static void resetPreferences() {
        showPlaceDetails = false;
    }

    /** Position du curseur au dernier rendu, pour la surbrillance de l'en-tête cliquable. */
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    /** Objet de butin survolé, dont l'infobulle est dessinée en dernier (au-dessus du reste). */
    private ItemStack hoveredDrop = ItemStack.EMPTY;

    public MobDetailScreen(Screen parent, EntityType<?> entityType, ResourceLocation entityId) {
        super(entityType.getDescription());
        this.parent = parent;
        this.entityType = entityType;
        this.entityId = entityId;
        this.category = EntityCatalog.categoryOf(entityType);
    }

    @Override
    protected void init() {
        // Demande la fiche au serveur (ne fait rien si elle est déjà en cache).
        ClientMobInfoCache.request(entityId);

        // Cri du mob à l'ouverture de la fiche.
        MobSoundPlayer.playFor(entityType);

        addRenderableWidget(Button.builder(
                        Component.translatable("faunapedia.detail.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, backButtonTop(), 100, 20)
                .build());
    }

    /** Position verticale du bouton « Retour ». Sert aussi de limite basse à la liste des biomes. */
    private int backButtonTop() {
        return this.height - 28;
    }

    /**
     * Clic sur l'en-tête « Biomes d'apparition » : ouvre la liste complète.
     *
     * <p>La fiche n'affiche que ce qui tient à l'écran ; l'écran dédié montre tout.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Dépliant du lieu de découverte.
        if (toggleLeft >= 0 && mouseX >= toggleLeft && mouseX <= toggleRight
                && mouseY >= toggleTop && mouseY <= toggleBottom) {
            showPlaceDetails = !showPlaceDetails;
            return true;
        }

        MobInfoPayload info = ClientMobInfoCache.get(entityId);
        if (info == null || info.spawnBiomes().isEmpty()) {
            return false;
        }

        int left = panelLeft();
        boolean onHeader = mouseX >= left && mouseX <= left + PANEL_WIDTH
                && mouseY >= biomeHeaderTop && mouseY <= biomeHeaderBottom;

        if (onHeader) {
            this.minecraft.setScreen(new BiomeListScreen(this, entityType, info.spawnBiomes()));
            return true;
        }
        return false;
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        // Réinitialisé à chaque image : drawDrops() le remplira si le curseur survole un objet.
        this.hoveredDrop = ItemStack.EMPTY;

        int left = panelLeft();
        int right = left + PANEL_WIDTH;

        // --- En-tête ----------------------------------------------------------------------------
        graphics.drawCenteredString(this.font, entityType.getDescription(), this.width / 2, 12, 0xFFFFD24B);
        graphics.drawCenteredString(this.font,
                Component.translatable(category.getTranslationKey()),
                this.width / 2, 24, category.getAccentColor());
        graphics.drawCenteredString(this.font, entityId.toString(), this.width / 2, 35, 0xFF6E6E6E);

        int contentTop = 50;

        // --- Portrait 3D du mob ------------------------------------------------------------------
        int portraitLeft = left + 8;
        graphics.fill(portraitLeft, contentTop, portraitLeft + PORTRAIT_SIZE, contentTop + PORTRAIT_SIZE,
                0x50000000);
        // Cadre à la couleur de la catégorie. Tracé à l'extérieur du portrait, pour ne pas rogner
        // la zone dans laquelle le modèle est dessiné.
        drawFrame(graphics, portraitLeft - FRAME_THICKNESS, contentTop - FRAME_THICKNESS,
                portraitLeft + PORTRAIT_SIZE + FRAME_THICKNESS, contentTop + PORTRAIT_SIZE + FRAME_THICKNESS,
                FRAME_THICKNESS, category.getAccentColor());
        boolean rendered = EntityIconRenderer.renderMob(graphics, entityType,
                portraitLeft, contentTop, PORTRAIT_SIZE,
                // Le mob suit légèrement le curseur, comme dans l'écran d'inventaire vanilla.
                (portraitLeft + PORTRAIT_SIZE / 2F - mouseX) / 12F,
                (contentTop + PORTRAIT_SIZE / 2F - mouseY) / 12F);
        if (!rendered) {
            // Repli si le renderer du mob a échoué : pastille de couleur.
            graphics.fill(portraitLeft + 20, contentTop + 20,
                    portraitLeft + PORTRAIT_SIZE - 20, contentTop + PORTRAIT_SIZE - 20,
                    EntityCatalog.colorFor(entityId));
        }

        // --- Colonne de droite : statistiques ----------------------------------------------------
        int statsLeft = portraitLeft + PORTRAIT_SIZE + 14;
        MobInfoPayload info = ClientMobInfoCache.get(entityId);

        if (info == null) {
            graphics.drawString(this.font, Component.translatable("faunapedia.detail.loading"),
                    statsLeft, contentTop + 10, 0xFF9A9A9A, false);
            return;
        }

        int y = contentTop;
        y = drawStat(graphics, statsLeft, y, "faunapedia.detail.health", format(info.maxHealth()), 0xFFE05B5B);
        if (info.attackDamage() > 0) {
            y = drawStat(graphics, statsLeft, y, "faunapedia.detail.attack", format(info.attackDamage()), 0xFFE0A44B);
        }
        if (info.armor() > 0) {
            y = drawStat(graphics, statsLeft, y, "faunapedia.detail.armor", format(info.armor()), 0xFF9AA8B8);
        }
        if (info.movementSpeed() > 0) {
            y = drawStat(graphics, statsLeft, y, "faunapedia.detail.speed", format(info.movementSpeed() * 10F), 0xFF6ECB63);
        }
        y = drawStat(graphics, statsLeft, y, "faunapedia.detail.size",
                format(info.width()) + " × " + format(info.height()), 0xFFB0B0B0);

        // Journal de rencontre : quand ce mob a été vu pour la première fois, et combien de fois.
        y += 4;
        y = drawStat(graphics, statsLeft, y, "faunapedia.detail.first_seen",
                Component.translatable("faunapedia.detail.day", info.firstSeenDay()).getString(),
                0xFFC9A0DC);
        // Ligne des rencontres, suivie du dépliant. Le lieu de découverte est du CONTEXTE, pas une
        // caractéristique du mob : le laisser au même niveau que les PV ou la taille lui donnait un
        // poids qu'il n'a pas. Il est donc replié par défaut derrière un [+].
        int encountersLineY = y;
        String encountersValue = String.valueOf(info.encounters());
        y = drawStat(graphics, statsLeft, y, "faunapedia.detail.encounters", encountersValue, 0xFFC9A0DC);

        boolean hasPlace = info.firstSeenBiome().isPresent() || info.firstSeenPos().isPresent();
        if (hasPlace) {
            int labelWidth = this.font.width(Component.translatable("faunapedia.detail.encounters")) + 6;
            int markerX = statsLeft + labelWidth + this.font.width(encountersValue) + 6;
            String marker = showPlaceDetails ? "[-]" : "[+]";

            toggleLeft = markerX;
            toggleRight = markerX + this.font.width(marker);
            toggleTop = encountersLineY;
            toggleBottom = encountersLineY + 9;

            boolean hovered = lastMouseX >= toggleLeft && lastMouseX <= toggleRight
                    && lastMouseY >= toggleTop && lastMouseY <= toggleBottom;
            graphics.drawString(this.font, marker, markerX, encountersLineY,
                    hovered ? 0xFFFFD24B : 0xFF7A7A7A, true);

            if (showPlaceDetails) {
                if (info.firstSeenBiome().isPresent()) {
                    y = drawStat(graphics, statsLeft, y, "faunapedia.detail.first_biome",
                            biomeName(info.firstSeenBiome().get()).getString(), category.getAccentColor());
                }
                if (info.firstSeenPos().isPresent()) {
                    y = drawStat(graphics, statsLeft, y, "faunapedia.detail.first_place",
                            placeDescription(info.firstSeenPos().get()).getString(), 0xFF8FD3C7);
                }
            }
        } else {
            // Rien à déplier (mob découvert avant l'ajout du champ, ou débloqué par commande) :
            // pas de [+] trompeur qui n'ouvrirait sur rien.
            toggleLeft = toggleRight = toggleTop = toggleBottom = -1;
        }

        // --- Butins ------------------------------------------------------------------------------
        int sectionY = Math.max(y + 6, contentTop + PORTRAIT_SIZE + 8);
        sectionY = drawDrops(graphics, left, right, sectionY, info.drops());

        // --- Biomes d'apparition -----------------------------------------------------------------
        drawBiomes(graphics, left, sectionY, info.spawnBiomes());

        // L'infobulle se dessine en tout dernier pour passer au-dessus du reste de l'écran.
        if (!hoveredDrop.isEmpty()) {
            graphics.renderTooltip(this.font, hoveredDrop, mouseX, mouseY);
        }
    }

    /**
     * Nom traduit d'un biome. Les biomes suivent la convention {@code biome.<namespace>.<chemin>},
     * respectée par le vanilla comme par les mods — aucun nom n'est donc codé en dur.
     */
    private static Component biomeName(ResourceLocation id) {
        return Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
    }

    /**
     * Décrit le lieu d'une première rencontre <b>relativement au joueur</b> : distance et direction
     * s'il est dans la même dimension, mention de la dimension sinon.
     *
     * <p>Le calcul est fait côté client parce que lui seul connaît la position courante du joueur —
     * et parce qu'une distance n'a de sens qu'à l'instant où on la regarde.</p>
     */
    private Component placeDescription(GlobalPos place) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return Component.empty();
        }

        // Dimension différente : une distance n'aurait aucun sens entre deux mondes.
        if (!this.minecraft.level.dimension().equals(place.dimension())) {
            ResourceLocation dim = place.dimension().location();
            return Component.translatable("faunapedia.detail.other_dimension",
                    Component.translatable("dimension." + dim.getNamespace() + "." + dim.getPath()));
        }

        double dx = place.pos().getX() - this.minecraft.player.getX();
        double dz = place.pos().getZ() - this.minecraft.player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

        if (distance < 16) {
            return Component.translatable("faunapedia.detail.here");
        }
        return Component.translatable("faunapedia.detail.distance", distance, compass(dx, dz));
    }

    /**
     * Point cardinal (sur huit) du vecteur donné.
     *
     * <p>Repère Minecraft : +X vers l'est, +Z vers le sud. Le relèvement est donc mesuré depuis le
     * nord ({@code -Z}) en tournant vers l'est.</p>
     */
    private static Component compass(double dx, double dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        if (bearing < 0) {
            bearing += 360.0D;
        }
        String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        int index = (int) Math.round(bearing / 45.0D) % 8;
        return Component.translatable("faunapedia.direction." + points[index]);
    }

    /** Dessine un cadre creux d'une épaisseur donnée, bords inclus. */
    private static void drawFrame(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                  int thickness, int color) {
        graphics.fill(x0, y0, x1, y0 + thickness, color);  // haut
        graphics.fill(x0, y1 - thickness, x1, y1, color);  // bas
        graphics.fill(x0, y0, x0 + thickness, y1, color);  // gauche
        graphics.fill(x1 - thickness, y0, x1, y1, color);  // droite
    }

    /**
     * Couleur des libellés : le gris de base, tiré vers l'accent de la catégorie.
     *
     * <p>Ce sont les <b>libellés</b> qui portent la catégorie, jamais les valeurs : celles-ci gardent
     * leur code sémantique (PV en rouge, armure en gris acier…), qui permet de repérer une ligne d'un
     * coup d'œil. Tout teinter de la même couleur aurait remplacé cette information par une
     * redondance — la catégorie est déjà écrite sous le nom et portée par le cadre du portrait.</p>
     */
    private int labelColor() {
        return ColorUtil.mix(0xFF9A9A9A, category.getAccentColor(), 0.40F);
    }

    /**
     * Dessine une ligne « Libellé : valeur » et renvoie la position verticale suivante.
     *
     * <p>Avec <b>ombre portée</b> : ces lignes sont dessinées sur le monde simplement assombri, pas
     * sur un panneau opaque. Sans ombre, un texte y paraît délavé quelle que soit sa couleur — c'est
     * le contraste avec le fond qui manque, pas l'opacité (ces couleurs ont toujours été en alpha
     * plein).</p>
     */
    private int drawStat(GuiGraphics graphics, int x, int y, String key, String value, int color) {
        graphics.drawString(this.font, Component.translatable(key), x, y, labelColor(), true);
        int labelWidth = this.font.width(Component.translatable(key)) + 6;
        graphics.drawString(this.font, value, x + labelWidth, y, color, true);
        return y + 12;
    }

    private int drawDrops(GuiGraphics graphics, int left, int right, int y, List<ItemStack> drops) {
        graphics.fill(left, y, right, y + 1, ColorUtil.withAlpha(category.getAccentColor(), 0x55));
        y += 5;
        graphics.drawString(this.font, Component.translatable("faunapedia.detail.drops"),
                left + 4, y, 0xFFFFD24B, false);
        y += 13;

        if (drops.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("faunapedia.detail.drops_none"),
                    left + 8, y, 0xFF6E6E6E, false);
            return y + 16;
        }

        // Grille d'icônes d'objets. Les icônes sont celles du jeu : aucune image à fournir.
        int x = left + 8;
        int rowStart = y;
        for (ItemStack stack : drops) {
            if (x + 20 > right - 8) {
                x = left + 8;
                rowStart += 20;
            }
            graphics.renderItem(stack, x, rowStart);
            graphics.renderItemDecorations(this.font, stack, x, rowStart);

            // Survol : on retient l'objet, l'infobulle sera dessinée à la fin du rendu.
            if (lastMouseX >= x && lastMouseX < x + 16
                    && lastMouseY >= rowStart && lastMouseY < rowStart + 16) {
                hoveredDrop = stack;
            }

            x += 20;
        }
        return rowStart + 24;
    }

    private void drawBiomes(GuiGraphics graphics, int left, int y, List<ResourceLocation> biomes) {
        int right = left + PANEL_WIDTH;
        graphics.fill(left, y, right, y + 1, ColorUtil.withAlpha(category.getAccentColor(), 0x55));
        y += 5;

        // L'en-tête est cliquable : il ouvre la liste complète des biomes. On mémorise sa zone
        // pour que mouseClicked() sache la reconnaître.
        biomeHeaderTop = y - 2;
        biomeHeaderBottom = y + 10;
        boolean headerHovered = !biomes.isEmpty()
                && lastMouseX >= left && lastMouseX <= right
                && lastMouseY >= biomeHeaderTop && lastMouseY <= biomeHeaderBottom;

        graphics.drawString(this.font, Component.translatable("faunapedia.detail.biomes"),
                left + 4, y, 0xFFFFD24B, false);
        if (!biomes.isEmpty()) {
            Component hint = Component.translatable("faunapedia.detail.biomes_see_all", biomes.size());
            graphics.drawString(this.font, hint,
                    right - this.font.width(hint) - 4, y,
                    headerHovered ? 0xFFFFD24B : 0xFF8A8A8A, false);
        }
        y += 13;

        if (biomes.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("faunapedia.detail.biomes_none"),
                    left + 8, y, 0xFF6E6E6E, false);
            return;
        }

        // La liste est bornée par la place réellement disponible au-dessus du bouton « Retour ».
        // Sans cela, un mob présent dans beaucoup de biomes écrit par-dessus le bouton.
        final int lineHeight = 11;
        final int columnWidth = PANEL_WIDTH / 2 - 12;
        int limitY = backButtonTop() - 6;

        int maxRows = Math.max(1, (limitY - y) / lineHeight);
        int maxVisible = maxRows * 2;

        boolean truncated = biomes.size() > maxVisible;
        // Si on tronque, la dernière case est réservée au « +N autres ».
        int shown = truncated ? Math.max(0, maxVisible - 1) : biomes.size();

        int column = 0;
        int rowY = y;
        for (int i = 0; i < shown; i++) {
            ResourceLocation biome = biomes.get(i);
            // Clé de traduction vanilla des biomes : fonctionne aussi pour les biomes moddés.
            String name = Component.translatable(
                    "biome." + biome.getNamespace() + "." + biome.getPath()).getString();

            int x = left + 8 + column * (PANEL_WIDTH / 2 - 8);
            graphics.drawString(this.font, clip(name, columnWidth), x, rowY, 0xFFB0C4B0, false);

            column++;
            if (column >= 2) {
                column = 0;
                rowY += lineHeight;
            }
        }

        if (truncated) {
            int remaining = biomes.size() - shown;
            int x = left + 8 + column * (PANEL_WIDTH / 2 - 8);
            graphics.drawString(this.font,
                    Component.translatable("faunapedia.detail.biomes_more", remaining),
                    x, rowY, 0xFF7A8A7A, false);
        }
    }

    /** Raccourcit un texte trop large pour sa colonne, en ajoutant des points de suspension. */
    private String clip(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String shortened = this.font.plainSubstrByWidth(text, maxWidth - this.font.width("..."));
        return shortened + "...";
    }

    private static String format(float value) {
        // Affiche « 20 » plutôt que « 20.0 », mais garde une décimale si elle est significative.
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return String.valueOf(Math.round(value));
        }
        return String.format("%.1f", value);
    }

    /**
     * Échap remonte d'un niveau plutôt que de tout fermer, comme le bouton « Retour ».
     * Fermer directement ferait perdre la position de défilement dans la liste.
     */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Sécurité : si le mob venait à ne plus être considéré comme découvert, on ferme la fiche. */
    @Override
    public void tick() {
        if (!ClientFaunaPediaState.isDiscovered(entityId)) {
            this.minecraft.setScreen(parent);
        }
    }
}
