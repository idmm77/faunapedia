package com.strangevalley.faunapedia.compat.jei;

import com.strangevalley.faunapedia.FaunaPedia;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Plugin JEI : donne accès à l'objet survolé dans les listes de JEI.
 *
 * <p><b>JEI est facultatif.</b> Cette classe n'est chargée que par JEI lui-même, qui scanne
 * l'annotation {@link JeiPlugin}. Sans JEI installé, elle n'est jamais atteinte — et c'est la raison
 * pour laquelle tout le code touchant à son API vit dans ce paquet séparé, derrière
 * {@link JeiHoveredItem} : le reste du mod ne référence jamais une classe {@code mezz.jei.*}.</p>
 *
 * <p>Le plugin ne fait volontairement <b>rien</b> d'autre que retenir le runtime : il n'enregistre
 * aucune catégorie de recette, ne modifie aucun écran de JEI. Il sert uniquement à répondre à la
 * question « quel objet le curseur survole-t-il ? », à laquelle Minecraft seul ne sait pas répondre
 * pour une interface dessinée par un autre mod.</p>
 */
@JeiPlugin
public class FaunaPediaJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(FaunaPedia.MODID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // Ce plugin n'enregistrant aucune catégorie, JEI ne trace rien à son sujet : sans cette
        // ligne, rien dans les logs ne permettrait de vérifier qu'il a bien été capté.
        FaunaPedia.LOGGER.info("[FaunaPedia] Intégration JEI active (objet survolé accessible).");
    }

    @Override
    public void onRuntimeUnavailable() {
        // Indispensable : garder une référence à un runtime mort ferait consulter des écrans
        // appartenant à une partie quittée.
        runtime = null;
    }

    /**
     * Objet survolé dans une interface de JEI.
     *
     * <p>Trois sources consultées dans l'ordre : la liste latérale, les favoris, puis <b>l'écran de
     * recettes</b>. Cette dernière compte autant que les autres : c'est en consultant une recette
     * qu'on se demande le plus souvent d'où vient un ingrédient.</p>
     *
     * @return la pile survolée, ou {@link ItemStack#EMPTY} si le curseur n'est sur aucun objet de JEI
     */
    static ItemStack hoveredItem() {
        if (runtime == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack fromList = runtime.getIngredientListOverlay()
                    .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
            if (fromList != null && !fromList.isEmpty()) {
                return fromList;
            }

            ItemStack fromBookmarks = runtime.getBookmarkOverlay().getItemStackUnderMouse();
            if (fromBookmarks != null && !fromBookmarks.isEmpty()) {
                return fromBookmarks;
            }

            return runtime.getRecipesGui()
                    .getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                    .orElse(ItemStack.EMPTY);
        } catch (Throwable t) {
            // Une évolution d'API de JEI ne doit jamais casser la touche du FaunaPedia.
            FaunaPedia.LOGGER.debug("[FaunaPedia] Objet survolé JEI indisponible : {}", t.toString());
            return ItemStack.EMPTY;
        }
    }

    /**
     * La barre de recherche de JEI a-t-elle le focus clavier ?
     *
     * <p>Décisif : sans ce test, taper « p » dans la recherche de JEI alors qu'un objet est survolé
     * ouvrirait notre écran au lieu d'écrire la lettre.</p>
     */
    static boolean hasKeyboardFocus() {
        if (runtime == null) {
            return false;
        }
        try {
            return runtime.getIngredientListOverlay().hasKeyboardFocus();
        } catch (Throwable t) {
            // En cas de doute, on considère que le champ a le focus : ne pas voler la touche est
            // moins grave que casser la saisie.
            return true;
        }
    }
}
