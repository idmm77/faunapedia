package com.strangevalley.faunapedia.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Liste complète et défilante des biomes d'apparition d'un mob.
 *
 * <p>La fiche détaillée n'affiche que ce qui tient à l'écran ; cet écran-ci montre tout. Les biomes
 * sont regroupés par mod d'origine (namespace), ce qui rend la liste lisible quand plusieurs mods
 * ajoutent des biomes.</p>
 */
public class BiomeListScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int LIST_TOP = 44;
    private static final int LINE_HEIGHT = 11;
    private static final int SCROLL_SPEED = 16;

    /** Ligne affichable : soit un en-tête de mod, soit un biome. */
    private record Line(String text, boolean isHeader) {}

    private final MobDetailScreen parent;
    private final EntityType<?> entityType;
    private final List<Line> lines = new ArrayList<>();

    private double scrollAmount = 0.0D;
    private boolean draggingScrollbar = false;

    public BiomeListScreen(MobDetailScreen parent, EntityType<?> entityType,
                           List<ResourceLocation> biomes) {
        super(Component.translatable("faunapedia.biomes.title"));
        this.parent = parent;
        this.entityType = entityType;
        buildLines(biomes);
    }

    /** Regroupe les biomes par mod d'origine, chaque groupe précédé de son namespace. */
    private void buildLines(List<ResourceLocation> biomes) {
        List<ResourceLocation> sorted = new ArrayList<>(biomes);
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath));

        String currentNamespace = null;
        for (ResourceLocation biome : sorted) {
            if (!biome.getNamespace().equals(currentNamespace)) {
                currentNamespace = biome.getNamespace();
                lines.add(new Line(currentNamespace, true));
            }
            // Clé de traduction vanilla des biomes : fonctionne aussi pour les biomes moddés.
            String name = Component.translatable(
                    "biome." + biome.getNamespace() + "." + biome.getPath()).getString();
            lines.add(new Line(name, false));
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("faunapedia.detail.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int listBottom() {
        return this.height - 34;
    }

    private double maxScroll() {
        return Math.max(0, lines.size() * LINE_HEIGHT - (listBottom() - LIST_TOP));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scrollAmount = Mth.clamp(scrollAmount - scrollY * SCROLL_SPEED, 0.0D, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = panelLeft();
        int right = left + PANEL_WIDTH;
        int listBottom = listBottom();

        graphics.drawCenteredString(this.font, entityType.getDescription(), this.width / 2, 12, 0xFFFFD24B);
        graphics.drawCenteredString(this.font,
                Component.translatable("faunapedia.biomes.count", countBiomes()),
                this.width / 2, 26, 0xFFFFFFFF);

        graphics.fill(left, LIST_TOP - 2, right, listBottom + 2, 0x60000000);

        graphics.enableScissor(left, LIST_TOP, right, listBottom);
        int y = LIST_TOP - (int) Math.round(scrollAmount);
        for (Line line : lines) {
            if (y + LINE_HEIGHT >= LIST_TOP && y <= listBottom) {
                if (line.isHeader()) {
                    graphics.drawString(this.font, line.text(), left + 6, y, 0xFF8A8AC0, false);
                } else {
                    graphics.drawString(this.font, line.text(), left + 16, y, 0xFFB0C4B0, false);
                }
            }
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();

        ScrollBar.render(graphics, right + 4, LIST_TOP, listBottom,
                scrollAmount, maxScroll(), lines.size() * LINE_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        Double target = ScrollBar.scrollFromClick(mouseX, mouseY,
                panelLeft() + PANEL_WIDTH + 4, LIST_TOP, listBottom(), maxScroll());
        if (target != null) {
            scrollAmount = target;
            draggingScrollbar = true;
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

    private int countBiomes() {
        return (int) lines.stream().filter(line -> !line.isHeader()).count();
    }

    /** Échap remonte à la fiche du mob plutôt que de tout fermer. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
