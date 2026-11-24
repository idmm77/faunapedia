package com.strangevalley.faunapedia.client.screen;

import com.strangevalley.faunapedia.MobCategories.Category;
import com.strangevalley.faunapedia.client.ClientDropIndex;
import com.strangevalley.faunapedia.client.EntityCatalog;
import com.strangevalley.faunapedia.client.EntityIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * « Qui lâche cet objet ? » — mobs connus dont le butin contient l'objet survolé.
 *
 * <p>Ouvert depuis l'inventaire, en survolant un objet et en appuyant sur la touche du FaunaPedia.
 * La liste ne contient que des mobs <b>déjà découverts</b> : le filtrage est fait par le serveur
 * avant l'envoi de l'index, jamais ici — sinon un client modifié le contournerait.</p>
 */
public class ItemSourcesScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int LIST_TOP = 58;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 20;
    private static final int SCROLL_SPEED = 16;

    private final Screen parent;
    private final ItemStack stack;
    private final ResourceLocation itemId;

    private final List<EntityType<?>> sources = new ArrayList<>();
    private double scrollAmount = 0.0D;

    public ItemSourcesScreen(Screen parent, ItemStack stack, ResourceLocation itemId) {
        super(Component.translatable("faunapedia.sources.title"));
        this.parent = parent;
        this.stack = stack;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        // L'index n'est demandé qu'ici : la plupart des parties ne s'en serviront jamais.
        ClientDropIndex.request();
        rebuild();

        addRenderableWidget(Button.builder(
                        Component.translatable("faunapedia.detail.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    private void rebuild() {
        sources.clear();
        for (ResourceLocation entityId : ClientDropIndex.mobsDropping(itemId)) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresent(sources::add);
        }
        sources.sort(Comparator.comparing(type -> type.getDescription().getString()));
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int listBottom() {
        return this.height - 36;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // L'index arrive de façon asynchrone : tant qu'il n'est pas là, la liste reste vide. On la
        // reconstruit dès qu'elle devient disponible plutôt que d'attendre une réouverture.
        if (sources.isEmpty() && ClientDropIndex.isReady()) {
            rebuild();
        }

        int left = panelLeft();
        int right = left + PANEL_WIDTH;

        // --- En-tête : l'objet concerné ----------------------------------------------------------
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFD24B);
        graphics.renderItem(stack, left + 8, 30);
        graphics.drawString(this.font, stack.getHoverName(), left + 32, 36, 0xFFFFFFFF, true);

        graphics.fill(left, LIST_TOP - 6, right, LIST_TOP - 5, 0x40FFFFFF);

        if (!ClientDropIndex.isReady()) {
            graphics.drawCenteredString(this.font, Component.translatable("faunapedia.sources.loading"),
                    this.width / 2, LIST_TOP + 20, 0xFF9A9A9A);
            return;
        }
        if (sources.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("faunapedia.sources.none"),
                    this.width / 2, LIST_TOP + 20, 0xFF9A9A9A);
            return;
        }

        // --- Liste défilante ---------------------------------------------------------------------
        int bottom = listBottom();
        graphics.enableScissor(left, LIST_TOP, right, bottom);

        int y = LIST_TOP - (int) scrollAmount;
        for (EntityType<?> type : sources) {
            if (y + ROW_HEIGHT >= LIST_TOP && y <= bottom) {
                drawRow(graphics, type, left, right, y, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }

        graphics.disableScissor();
    }

    private void drawRow(GuiGraphics graphics, EntityType<?> type, int left, int right, int y,
                         int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2;
        Category category = EntityCatalog.categoryOf(type);

        graphics.fill(left + 4, y, right, y + ROW_HEIGHT - 2, hovered ? 0x45FFFFFF : 0x35000000);
        // Liseré de catégorie, comme dans le catalogue : la couleur veut dire la même chose partout.
        graphics.fill(left + 4, y, left + 6, y + ROW_HEIGHT - 2, category.getAccentColor());

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        boolean rendered = EntityIconRenderer.renderMob(graphics, type, left + 10, y + 1, ICON_SIZE, 0F, 0F);
        if (!rendered && entityId != null) {
            graphics.fill(left + 12, y + 3, left + 10 + ICON_SIZE - 2, y + ICON_SIZE - 1,
                    EntityCatalog.colorFor(entityId));
        }

        graphics.drawString(this.font, type.getDescription(), left + 36, y + 7, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int left = panelLeft();
        int right = left + PANEL_WIDTH;
        if (mouseX < left || mouseX > right || mouseY < LIST_TOP || mouseY > listBottom()) {
            return false;
        }

        int index = (int) ((mouseY - LIST_TOP + scrollAmount) / ROW_HEIGHT);
        if (index < 0 || index >= sources.size()) {
            return false;
        }

        EntityType<?> type = sources.get(index);
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entityId != null) {
            this.minecraft.setScreen(new MobDetailScreen(this, type, entityId));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollAmount = Mth.clamp(scrollAmount - deltaY * SCROLL_SPEED, 0.0D, maxScroll());
        return true;
    }

    private double maxScroll() {
        int contentHeight = sources.size() * ROW_HEIGHT;
        return Math.max(0, contentHeight - (listBottom() - LIST_TOP));
    }

    /** Échap remonte à l'écran d'où l'on vient (l'inventaire, en général). */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
