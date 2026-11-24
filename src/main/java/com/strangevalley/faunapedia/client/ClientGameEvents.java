package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.FaunaPediaConfig;
import com.strangevalley.faunapedia.client.screen.FaunaPediaScreen;
import com.strangevalley.faunapedia.client.screen.ItemSourcesScreen;
import com.strangevalley.faunapedia.client.screen.MobDetailScreen;
import com.strangevalley.faunapedia.compat.jei.JeiHoveredItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Événements de jeu côté client : ici, l'écoute de la touche d'ouverture du FaunaPedia.
 */
@EventBusSubscriber(modid = FaunaPedia.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // Recycle les exemplaires d'icônes sortis du viewport (voir MobSampleCache). Fait à chaque
        // tick pour couvrir aussi les toasts, qui rendent hors de tout écran.
        MobSampleCache.sweep();

        // consumeClick() dépile les appuis mis en file depuis le dernier tick
        // (évite d'ouvrir le menu plusieurs fois pour un seul appui).
        while (FaunaPediaKeyMappings.OPEN_FAUNAPEDIA.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new FaunaPediaScreen());
            }
        }
    }

    /**
     * Touche du FaunaPedia pressée <b>alors qu'un écran est ouvert</b>.
     *
     * <p>{@code consumeClick()} ne fonctionne que hors écran : quand une interface est affichée, les
     * touches passent par cet événement. On y traite le cas « inventaire ouvert, curseur sur un
     * objet » : la touche montre alors les mobs qui lâchent cet objet.</p>
     *
     * <p>La touche change donc de sens selon le contexte. C'est volontaire — appuyer sur la touche
     * du FaunaPedia en désignant un objet pose une question précise, et y répondre par le catalogue
     * complet serait moins utile. Sans objet sous le curseur, l'événement n'est pas consommé et
     * l'inventaire se comporte normalement.</p>
     */
    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!FaunaPediaKeyMappings.OPEN_FAUNAPEDIA.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }

        // Ne jamais voler une touche à un champ de saisie : sans ces deux gardes, taper « p » dans
        // la recherche de JEI ou dans une enclume ouvrirait notre écran au lieu d'écrire la lettre.
        if (event.getScreen().getFocused() instanceof EditBox || JeiHoveredItem.hasKeyboardFocus()) {
            return;
        }

        ItemStack stack = hoveredItem(event.getScreen());
        if (stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }

        Minecraft.getInstance().setScreen(new ItemSourcesScreen(event.getScreen(), stack.copy(), itemId));
        // Consommé : sans cela, la touche serait AUSSI interprétée par l'inventaire (par exemple
        // comme un raccourci de barre d'action si elle y est assignée).
        event.setCanceled(true);
    }

    /**
     * Objet sous le curseur, qu'il appartienne à l'inventaire ou à une liste de JEI.
     *
     * <p>Le slot vanilla est consulté en premier : c'est le cas courant, et il ne dépend d'aucun
     * autre mod. JEI n'est interrogé qu'en repli, à travers {@code JeiHoveredItem} qui renvoie une
     * pile vide si le mod est absent — le mod reste donc parfaitement fonctionnel sans lui.</p>
     */
    private static ItemStack hoveredItem(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                return slot.getItem();
            }
        }
        // Écran de recettes de JEI, liste latérale, favoris : aucun n'est un écran de conteneur,
        // d'où l'absence de filtre sur le type d'écran ici.
        return JeiHoveredItem.get();
    }

    /**
     * Astuce ajoutée à l'infobulle d'un objet, rappelant la touche qui montre ses sources.
     *
     * <p>Sans elle, la fonctionnalité est invisible : rien n'indique au joueur qu'une touche fait
     * quelque chose ici. L'astuce n'apparaît que dans un écran de conteneur — ailleurs (dans une
     * infobulle de forge, de JEI…) la touche n'a pas cet effet et le rappel serait faux.</p>
     *
     * <p>La touche affichée est celle <b>réellement assignée</b> : elle est réassignable, et écrire
     * « P » en dur mentirait dès le premier joueur qui la change.</p>
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!FaunaPediaConfig.ENABLE_TOOLTIP_HINT.get()) {
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }
        // Exiger un objet réellement survolé — slot d'inventaire, liste de JEI ou écran de recettes.
        // L'astuce doit apparaître exactement là où la touche fonctionne, ni plus (promesse creuse)
        // ni moins (fonctionnalité invisible). C'est la même source que le gestionnaire de touche.
        if (hoveredItem(screen).isEmpty()) {
            return;
        }
        // Doré du mod plutôt que le GOLD vanilla (#FFAA00) : c'est la teinte des titres du
        // FaunaPedia, elle signale d'où vient cette ligne au milieu d'une infobulle.
        event.getToolTip().add(Component.translatable("faunapedia.tooltip.hint",
                        FaunaPediaKeyMappings.OPEN_FAUNAPEDIA.getTranslatedKeyMessage())
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFD24B)).withItalic(true)));
    }

    /**
     * Nettoyage à la déconnexion.
     *
     * <p>Indispensable : les exemplaires de mobs conservés pour dessiner les icônes référencent le
     * monde quitté, et les fiches en cache (butins, biomes) dépendent des données de ce monde.
     * Les réutiliser dans une autre partie donnerait des informations fausses.</p>
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EntityCatalog.reset();
        MobSampleCache.reset();
        EntityIconRenderer.reset();
        ClientMobInfoCache.clear();
        FaunaPediaScreen.resetPreferences();
        MobDetailScreen.resetPreferences();
    }

    private ClientGameEvents() {}
}
