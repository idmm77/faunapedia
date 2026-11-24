package com.strangevalley.faunapedia.client.screen;

import com.strangevalley.faunapedia.FaunaPediaConfig;
import com.strangevalley.faunapedia.MobCategories.Category;
import com.strangevalley.faunapedia.client.ClientDropIndex;
import com.strangevalley.faunapedia.client.ClientFaunaPediaState;
import com.strangevalley.faunapedia.client.ColorUtil;
import com.strangevalley.faunapedia.client.EntityCatalog;
import com.strangevalley.faunapedia.client.EntityIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Écran principal du FaunaPedia : liste défilante de tous les mobs, groupés par catégorie.
 *
 * <p>Chaque ligne montre soit le mob en couleur avec son nom (déjà rencontré), soit une silhouette
 * grisée avec « ??? » (jamais rencontré). L'en-tête de chaque section et l'en-tête global affichent
 * un compteur et un pourcentage de complétion.</p>
 */
public class FaunaPediaScreen extends Screen {

    // --- Constantes de mise en page -------------------------------------------------------------
    private static final int CONTENT_WIDTH = 280;
    private static final int HEADER_ROW_HEIGHT = 20;
    private static final int ENTRY_ROW_HEIGHT = 24;
    /** Côté de l'icône du mob, en pixels. */
    private static final int ICON_SIZE = 20;
    private static final int TOOLBAR_Y = 34;
    private static final int LIST_TOP = 56;
    private static final int LIST_BOTTOM_MARGIN = 14;
    private static final int SCROLL_SPEED = 16;

    /** Modes de tri appliqués À L'INTÉRIEUR de chaque catégorie. */
    private enum SortMode {
        ALPHA("faunapedia.sort.alpha"),
        MOD("faunapedia.sort.mod");

        private final String key;

        SortMode(String key) {
            this.key = key;
        }

        SortMode next() {
            return this == ALPHA ? MOD : ALPHA;
        }
    }

    /** Filtre appliqué à la liste : tout, seulement les découverts, seulement les inconnus. */
    private enum FilterMode {
        ALL("faunapedia.filter.all"),
        DISCOVERED("faunapedia.filter.discovered"),
        UNDISCOVERED("faunapedia.filter.undiscovered");

        private final String key;

        FilterMode(String key) {
            this.key = key;
        }

        FilterMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        boolean accepts(boolean discovered) {
            return switch (this) {
                case ALL -> true;
                case DISCOVERED -> discovered;
                case UNDISCOVERED -> !discovered;
            };
        }
    }

    // --- Lignes affichables ---------------------------------------------------------------------
    private sealed interface Row permits HeaderRow, EntryRow {
        int height();
    }

    /** En-tête de catégorie (avec son propre compteur de complétion). */
    private record HeaderRow(Category category, int done, int total) implements Row {
        @Override
        public int height() {
            return HEADER_ROW_HEIGHT;
        }
    }

    /** Ligne d'un mob. */
    private record EntryRow(EntityType<?> type, ResourceLocation id, boolean discovered,
                            Category category) implements Row {
        @Override
        public int height() {
            return ENTRY_ROW_HEIGHT;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private double scrollAmount = 0.0D;
    private boolean draggingScrollbar = false;
    /** Disponibilité de l'index des butins au dernier rendu, pour détecter son arrivée. */
    private boolean indexWasReady = false;

    // Tri, filtre et recherche sont STATIQUES : une nouvelle instance d'écran est créée à chaque
    // appui sur la touche, et repartir de zéro à chaque ouverture serait pénible. Ils sont remis
    // à zéro à la déconnexion (voir ClientGameEvents).
    private static SortMode sortMode = SortMode.ALPHA;
    private static FilterMode filterMode = FilterMode.ALL;
    private static String savedQuery = "";
    /** Les défauts de config n'ont pas encore été lus dans cette session. */
    private static boolean defaultsPending = true;

    /** Remet les préférences d'affichage à leur valeur par défaut (changement de partie). */
    public static void resetPreferences() {
        savedQuery = "";
        // On ne recopie pas les défauts tout de suite : la config se relit à la prochaine ouverture,
        // ce qui évite de dépendre de l'ordre entre la déconnexion et le rechargement des configs.
        defaultsPending = true;
    }

    /**
     * Aligne le tri et le filtre courants sur les valeurs choisies dans les réglages.
     *
     * <p>Appelé à la première ouverture du menu de chaque session, et à chaque modification depuis
     * l'écran de réglages — pour que le changement soit visible immédiatement plutôt qu'à la partie
     * suivante.</p>
     */
    public static void applyDefaultsFromConfig() {
        sortMode = "mod".equals(FaunaPediaConfig.DEFAULT_SORT.get()) ? SortMode.MOD : SortMode.ALPHA;
        filterMode = switch (FaunaPediaConfig.DEFAULT_FILTER.get()) {
            case "discovered" -> FilterMode.DISCOVERED;
            case "undiscovered" -> FilterMode.UNDISCOVERED;
            default -> FilterMode.ALL;
        };
        defaultsPending = false;
    }

    private EditBox searchBox;
    private Button sortButton;
    private Button filterButton;


    public FaunaPediaScreen() {
        super(Component.translatable("faunapedia.screen.title"));
    }

    @Override
    protected void init() {
        // Construit le catalogue si ce n'est pas déjà fait (parcours du registre EntityType).
        EntityCatalog.ensureBuilt();

        // Index des butins : nécessaire pour que la recherche porte aussi sur les objets lâchés.
        // Demandé ici plutôt qu'à la connexion, et sans attendre la réponse.
        ClientDropIndex.request();

        // Première ouverture de la session : on part du tri et du filtre choisis dans les réglages.
        if (defaultsPending) {
            applyDefaultsFromConfig();
        }

        int left = contentLeft();

        searchBox = new EditBox(this.font, left + 2, TOOLBAR_Y, 138, 18,
                Component.translatable("faunapedia.screen.search"));
        searchBox.setHint(Component.translatable("faunapedia.screen.search"));
        searchBox.setMaxLength(64);
        // Restaure la recherche en cours : init() est rappelé au retour depuis une fiche détaillée,
        // et perdre le texte saisi à chaque aller-retour serait pénible.
        searchBox.setValue(savedQuery);
        // À chaque frappe, on reconstruit la liste filtrée.
        searchBox.setResponder(value -> {
            savedQuery = value;
            rebuildRows();
        });
        addRenderableWidget(searchBox);

        sortButton = Button.builder(sortLabel(), button -> {
            sortMode = sortMode.next();
            button.setMessage(sortLabel());
            rebuildRows();
        }).bounds(left + 144, TOOLBAR_Y, 66, 18).build();
        addRenderableWidget(sortButton);

        filterButton = Button.builder(filterLabel(), button -> {
            filterMode = filterMode.next();
            button.setMessage(filterLabel());
            // On repart du haut : après un changement de filtre, la position de défilement
            // précédente n'a plus de sens.
            scrollAmount = 0.0D;
            rebuildRows();
        }).bounds(left + 214, TOOLBAR_Y, 66, 18).build();
        addRenderableWidget(filterButton);

        // Bouton « Réglages », placé sur la ligne du titre : la barre d'outils est pleine, et y
        // insérer un bouton aurait obligé à rétrécir la recherche, le tri et le filtre.
        addRenderableWidget(Button.builder(
                        Component.translatable("faunapedia.screen.settings"),
                        button -> this.minecraft.setScreen(new FaunaPediaSettingsScreen(this)))
                .bounds(left + CONTENT_WIDTH - 62, 4, 62, 20)
                .build());

        rebuildRows();
    }

    private Component sortLabel() {
        return Component.translatable(sortMode.key);
    }

    private Component filterLabel() {
        return Component.translatable(filterMode.key);
    }

    private int contentLeft() {
        return (this.width - CONTENT_WIDTH) / 2;
    }

    private int listBottom() {
        return this.height - LIST_BOTTOM_MARGIN;
    }

    // --- Construction de la liste ---------------------------------------------------------------

    /** Reconstruit les lignes affichées en tenant compte du filtre de recherche et du tri. */
    private void rebuildRows() {
        rows.clear();

        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        Map<Category, List<EntityType<?>>> grouped = EntityCatalog.getByCategory();

        for (Category category : Category.values()) {
            List<EntityType<?>> types = grouped.getOrDefault(category, List.of());
            List<EntryRow> entries = new ArrayList<>();

            for (EntityType<?> type : types) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (id == null) {
                    continue;
                }
                boolean discovered = ClientFaunaPediaState.isDiscovered(id);
                if (!filterMode.accepts(discovered)) {
                    continue;
                }
                if (!query.isEmpty() && !matchesQuery(type, id, discovered, query)) {
                    continue;
                }
                entries.add(new EntryRow(type, id, discovered, category));
            }

            if (entries.isEmpty()) {
                continue;
            }

            entries.sort(comparator());
            // Le compteur de l'en-tête reflète TOUTE la catégorie, pas seulement le résultat filtré.
            rows.add(new HeaderRow(category, EntityCatalog.discoveredCountIn(category), types.size()));
            rows.addAll(entries);
        }

        clampScroll();
    }

    /**
     * Filtre de recherche.
     *
     * <p>Un mob non découvert ne peut être trouvé que par le nom de son mod d'origine : chercher son
     * nom ne doit pas révéler une information que le joueur n'a pas encore débloquée.</p>
     *
     * <p>La recherche porte aussi sur les <b>butins</b> : taper « cuir » fait remonter les mobs qui
     * en lâchent. C'est le pendant du survol d'inventaire — celui-ci part de l'objet qu'on a, la
     * recherche de l'objet qu'on veut. Le butin n'est testé qu'après la garde ci-dessous, donc un
     * mob non découvert reste introuvable par ce biais.</p>
     */
    private boolean matchesQuery(EntityType<?> type, ResourceLocation id, boolean discovered, String query) {
        if (id.getNamespace().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (!discovered) {
            return false;
        }
        return type.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
                || id.getPath().toLowerCase(Locale.ROOT).contains(query)
                || matchesDrop(id, query);
    }

    /** Ce mob lâche-t-il un objet dont le nom correspond à la recherche ? */
    private static boolean matchesDrop(ResourceLocation id, String query) {
        for (ResourceLocation itemId : ClientDropIndex.dropsOf(id)) {
            // Nom traduit d'abord (ce que le joueur tape), identifiant ensuite (« leather »).
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR
                    && item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            if (itemId.getPath().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private Comparator<EntryRow> comparator() {
        Comparator<EntryRow> byName =
                Comparator.comparing(row -> row.type().getDescription().getString().toLowerCase(Locale.ROOT));
        if (sortMode == SortMode.MOD) {
            // Sous-groupement par mod d'origine (namespace de la ResourceLocation), puis par nom.
            return Comparator.<EntryRow, String>comparing(row -> row.id().getNamespace()).thenComparing(byName);
        }
        return byName;
    }

    // --- Défilement -----------------------------------------------------------------------------

    private int totalContentHeight() {
        int total = 0;
        for (Row row : rows) {
            total += row.height();
        }
        return total;
    }

    private double maxScroll() {
        return Math.max(0, totalContentHeight() - (listBottom() - LIST_TOP));
    }

    private void clampScroll() {
        scrollAmount = Mth.clamp(scrollAmount, 0.0D, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scrollAmount = Mth.clamp(scrollAmount - scrollY * SCROLL_SPEED, 0.0D, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // --- Interaction ----------------------------------------------------------------------------

    /** Ouvre la fiche détaillée du mob cliqué, uniquement s'il a déjà été rencontré. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Laisse d'abord les widgets (recherche, tri) réagir.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int left = contentLeft();

        // Barre de défilement : clic pour sauter, puis glissement.
        Double target = ScrollBar.scrollFromClick(mouseX, mouseY,
                left + CONTENT_WIDTH + 4, LIST_TOP, listBottom(), maxScroll());
        if (target != null) {
            scrollAmount = target;
            draggingScrollbar = true;
            return true;
        }

        boolean insideList = mouseX >= left && mouseX <= left + CONTENT_WIDTH
                && mouseY >= LIST_TOP && mouseY < listBottom();
        if (!insideList) {
            return false;
        }

        EntryRow clicked = entryAt(mouseY);
        // Un mob non découvert n'est pas cliquable : aucune information ne doit fuiter.
        if (clicked != null && clicked.discovered()) {
            // Ouvrir la fiche retire le badge « nouveau ».
            ClientFaunaPediaState.markSeen(clicked.id());
            this.minecraft.setScreen(new MobDetailScreen(this, clicked.type(), clicked.id()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollAmount = ScrollBar.scrollFromPosition(mouseY, LIST_TOP, listBottom(), maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Retrouve la ligne de mob située sous une position verticale donnée, ou {@code null}. */
    private EntryRow entryAt(double mouseY) {
        int y = LIST_TOP - (int) Math.round(scrollAmount);
        for (Row row : rows) {
            int rowHeight = row.height();
            if (mouseY >= y && mouseY < y + rowHeight) {
                return row instanceof EntryRow entry ? entry : null;
            }
            y += rowHeight;
        }
        return null;
    }

    // --- Rendu ----------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Rend le fond assombri puis les widgets (barre de recherche, bouton de tri).
        super.render(graphics, mouseX, mouseY, partialTick);

        // L'index des butins arrive de façon asynchrone. S'il atterrit alors qu'une recherche est
        // déjà saisie, la liste affichée l'ignore encore : on la reconstruit une fois, à l'arrivée.
        boolean indexReady = ClientDropIndex.isReady();
        if (indexReady != indexWasReady) {
            indexWasReady = indexReady;
            if (!savedQuery.isEmpty()) {
                rebuildRows();
            }
        }

        int left = contentLeft();
        int right = left + CONTENT_WIDTH;

        renderTitleAndCounter(graphics, left, right);

        int listBottom = listBottom();
        // Cadre de la zone de liste.
        graphics.fill(left, LIST_TOP - 2, right, listBottom + 2, 0x60000000);

        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("faunapedia.screen.empty"),
                    this.width / 2, LIST_TOP + 10, 0xFF9A9A9A);
            return;
        }

        // « Scissor » = découpe : tout ce qui dépasse de la zone de liste est masqué,
        // ce qui donne l'effet de défilement propre.
        graphics.enableScissor(left, LIST_TOP, right, listBottom);
        int y = LIST_TOP - (int) Math.round(scrollAmount);
        for (Row row : rows) {
            int rowHeight = row.height();
            // On ne dessine que les lignes réellement visibles (optimisation).
            if (y + rowHeight >= LIST_TOP && y <= listBottom) {
                if (row instanceof HeaderRow header) {
                    renderHeaderRow(graphics, header, left, y);
                } else if (row instanceof EntryRow entry) {
                    renderEntryRow(graphics, entry, left, y, mouseX, mouseY);
                }
            }
            y += rowHeight;
        }
        graphics.disableScissor();

        ScrollBar.render(graphics, right + 4, LIST_TOP, listBottom,
                scrollAmount, maxScroll(), totalContentHeight());
    }

    private void renderTitleAndCounter(GuiGraphics graphics, int left, int right) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFD24B);

        int done = EntityCatalog.discoveredCount();
        int total = EntityCatalog.totalCount();
        Component counter = Component.translatable("faunapedia.screen.counter",
                done, total, EntityCatalog.percentage(done, total));
        graphics.drawCenteredString(this.font, counter, this.width / 2, 20, 0xFFFFFFFF);

        // Barre de progression globale.
        int barY = 30;
        int barWidth = right - left;
        graphics.fill(left, barY, right, barY + 2, 0xFF303030);
        if (total > 0) {
            int filled = (int) (barWidth * (done / (double) total));
            graphics.fill(left, barY, left + filled, barY + 2, 0xFF4BD97B);
        }
    }

    private void renderHeaderRow(GuiGraphics graphics, HeaderRow header, int left, int y) {
        int right = left + CONTENT_WIDTH;
        graphics.fill(left, y + 2, right, y + HEADER_ROW_HEIGHT - 2, 0x50000000);
        // Trait d'accent aux couleurs de la catégorie.
        graphics.fill(left, y + 2, left + 3, y + HEADER_ROW_HEIGHT - 2, header.category().getAccentColor());

        Component label = Component.translatable("faunapedia.category.header",
                Component.translatable(header.category().getTranslationKey()),
                header.done(),
                header.total(),
                EntityCatalog.percentage(header.done(), header.total()));
        graphics.drawString(this.font, label, left + 8, y + 6, header.category().getAccentColor(), false);
    }

    private void renderEntryRow(GuiGraphics graphics, EntryRow entry, int left, int y,
                                int mouseX, int mouseY) {
        int right = left + CONTENT_WIDTH;
        int top = y + 1;
        int bottom = y + ENTRY_ROW_HEIGHT - 1;

        // Surbrillance au survol, uniquement pour les lignes cliquables (mobs découverts).
        boolean hovered = entry.discovered()
                && mouseX >= left + 4 && mouseX <= right
                && mouseY >= top && mouseY < bottom
                && mouseY >= LIST_TOP && mouseY < listBottom();

        graphics.fill(left + 4, top, right, bottom, hovered ? 0x45FFFFFF : 0x35000000);
        graphics.fill(left + 4, top, left + 6, bottom, entry.category().getAccentColor());

        int iconLeft = left + 10;
        int iconTop = top + 1;

        if (entry.discovered()) {
            // Rendu 3D du vrai mob : c'est son propre renderer qui est appelé, donc les mobs
            // ajoutés par d'autres mods ont automatiquement la bonne icône, sans image fournie.
            boolean rendered = EntityIconRenderer.renderMob(
                    graphics, entry.type(), iconLeft, iconTop, ICON_SIZE, 0.0F, 0.0F);
            if (!rendered) {
                // Repli si le renderer du mob a échoué : pastille de couleur.
                graphics.fill(iconLeft + 2, iconTop + 2, iconLeft + ICON_SIZE - 2, iconTop + ICON_SIZE - 2,
                        EntityCatalog.colorFor(entry.id()));
            }
        } else {
            // Mob non rencontré : halo carré teinté par la catégorie + « ? » en pixels. On ne dessine
            // surtout PAS le mob, même en silhouette — sa forme suffirait à l'identifier, ce qui
            // viderait la découverte de son sens. La couleur de catégorie ne révèle rien de neuf : la
            // liste est déjà groupée par catégorie, l'appartenance est donc déjà connue.
            drawUnknownIcon(graphics, iconLeft, iconTop, entry.category().getAccentColor());
        }

        // Nom : masqué tant que le mob n'a pas été rencontré.
        Component name = entry.discovered()
                ? entry.type().getDescription()
                : Component.translatable("faunapedia.entry.unknown");
        int nameColor = entry.discovered() ? 0xFFFFFFFF : 0xFF6E6E6E;
        graphics.drawString(this.font, name, left + 36, top + 3, nameColor, false);

        // Indice toujours visible : le mod d'origine (namespace), utile pour situer un mob inconnu.
        graphics.drawString(this.font, entry.id().getNamespace(), left + 36, top + 13, 0xFF5A5A5A, false);

        // Chevron indiquant que la ligne ouvre une fiche.
        if (hovered) {
            graphics.drawString(this.font, ">", right - 12, top + 7, 0xFFFFD24B, false);
        }

        // Badge « new » : dessiné en dernier (au-dessus du reste) pour un mob fraîchement
        // découvert et pas encore consulté. Placé à DROITE, juste avant le chevron : position fixe,
        // jamais masquée par un grand mob, et alignée d'une ligne à l'autre.
        if (entry.discovered() && ClientFaunaPediaState.isUnseen(entry.id())) {
            drawNewBadge(graphics, right, top);
        }
    }

    /** Badge doré « ★ new » aligné à droite, juste avant la zone du chevron. */
    private void drawNewBadge(GuiGraphics graphics, int right, int top) {
        final int gold = 0xFFFFD24B;
        Component word = Component.translatable("faunapedia.badge.new");
        int wordWidth = this.font.width(word);
        // Le mot se termine avant la zone du chevron (dessiné à right - 12) ; l'étoile (7 px) le précède.
        int wordX = right - 14 - wordWidth;
        int starX = wordX - 9;
        drawStar(graphics, starX, top + 4, gold);
        graphics.drawString(this.font, word, wordX, top + 3, gold, true);
    }

    /**
     * Icône d'un mob non découvert : dégradé carré teinté par la catégorie surmonté d'un « ? ».
     *
     * <p>Trois teintes concentriques, subtiles : plus <b>foncée au centre</b>, plus <b>claire au
     * bord</b>, toutes opaques (aucun fondu vers le transparent). Un cadre gris entoure le tout. Rien
     * n'est dessiné du mob lui-même — seule la couleur, déjà connue via la section, varie.</p>
     */
    private void drawUnknownIcon(GuiGraphics graphics, int iconLeft, int iconTop, int accent) {
        int x0 = iconLeft;
        int y0 = iconTop;
        int side = ICON_SIZE;
        int x1 = x0 + side;
        int y1 = y0 + side;

        // Palette bâtie directement sur la couleur de catégorie, éclaircie vers un pastel : elle reste
        // franchement colorée au lieu de virer au gris. Les trois carrés vont du plus pastel (bord) au
        // plus saturé (centre) — donc plus clair au bord, plus foncé au centre.
        int edge = ColorUtil.mix(accent, 0xFFFFFFFF, 0.62F);
        int mid = ColorUtil.mix(accent, 0xFFFFFFFF, 0.40F);
        int center = ColorUtil.mix(accent, 0xFFFFFFFF, 0.18F);

        // Carrés concentriques : chaque remplissage recouvre le précédent (bord clair → centre foncé).
        graphics.fill(x0, y0, x1, y1, edge);
        graphics.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, mid);
        graphics.fill(x0 + 8, y0 + 8, x1 - 8, y1 - 8, center);

        // Cadre gris, tracé sur le pourtour.
        int frame = 0xFF6E6E6E;
        graphics.fill(x0, y0, x1, y0 + 1, frame);
        graphics.fill(x0, y1 - 1, x1, y1, frame);
        graphics.fill(x0, y0, x0 + 1, y1, frame);
        graphics.fill(x1 - 1, y0, x1, y1, frame);

        // « ? » en pixels, dans la teinte de catégorie (saturée pour ressortir du pastel), avec une
        // ombre douce d'un pixel prise dans la même teinte — jamais de noir, qui donnait l'aspect terne.
        int glyphW = QUESTION_SHAPE[0].length() * QUESTION_SCALE;
        int glyphH = QUESTION_SHAPE.length * QUESTION_SCALE;
        int qx = x0 + (side - glyphW) / 2;
        int qy = y0 + (side - glyphH) / 2;
        drawPixelQuestion(graphics, qx + 1, qy + 1, ColorUtil.mix(accent, 0xFF000000, 0.45F));
        drawPixelQuestion(graphics, qx, qy, ColorUtil.mix(accent, 0xFF000000, 0.12F));
    }

    /**
     * Motif de point d'interrogation (5×7), dessiné pixel par pixel comme {@link #STAR_SHAPE} — sans
     * police ni texture. Dessiné à l'échelle {@link #QUESTION_SCALE} (traits de 2 px, style StarCatcher).
     */
    private static final String[] QUESTION_SHAPE = {
            ".###.",
            "#...#",
            "...#.",
            "..#..",
            "..#..",
            ".....",
            "..#..",
    };

    private static final int QUESTION_SCALE = 2;

    private void drawPixelQuestion(GuiGraphics graphics, int x, int y, int color) {
        for (int row = 0; row < QUESTION_SHAPE.length; row++) {
            String line = QUESTION_SHAPE[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    int px = x + col * QUESTION_SCALE;
                    int py = y + row * QUESTION_SCALE;
                    graphics.fill(px, py, px + QUESTION_SCALE, py + QUESTION_SCALE, color);
                }
            }
        }
    }

    /**
     * Motif d'étoile à 5 branches (7×7), dessiné pixel par pixel — aucun caractère de police ni
     * texture, donc un rendu garanti et identique partout. {@code x}/{@code y} = coin haut-gauche.
     */
    private static final String[] STAR_SHAPE = {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            "##...##",
    };

    private void drawStar(GuiGraphics graphics, int x, int y, int color) {
        for (int row = 0; row < STAR_SHAPE.length; row++) {
            String line = STAR_SHAPE[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }


    /** Le jeu continue de tourner en arrière-plan (indispensable en multijoueur). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
