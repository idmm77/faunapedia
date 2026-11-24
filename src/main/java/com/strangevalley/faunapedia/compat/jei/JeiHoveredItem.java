package com.strangevalley.faunapedia.compat.jei;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Point d'entrée unique du reste du mod vers JEI.
 *
 * <p>C'est la <b>seule</b> classe que le code non-compat référence. Elle n'expose aucun type de
 * {@code mezz.jei.*} dans sa signature, et ne touche {@link FaunaPediaJeiPlugin} qu'après avoir
 * vérifié que JEI est chargé. Sans cette indirection, la simple présence d'un appel ferait charger
 * la classe du plugin — et donc les classes de JEI — sur une installation qui ne l'a pas, avec un
 * {@code NoClassDefFoundError} à la clé.</p>
 *
 * <p>C'est le même motif de chargement paresseux que celui employé pour les gestionnaires de paquets
 * client, qui permet au mod de tourner sur serveur dédié.</p>
 */
public final class JeiHoveredItem {

    /** Résultat du test de présence, fait une seule fois. */
    private static Boolean jeiLoaded;

    /**
     * Objet actuellement survolé dans une interface de JEI.
     *
     * @return la pile survolée, ou {@link ItemStack#EMPTY} si JEI est absent ou si rien n'est survolé
     */
    public static ItemStack get() {
        if (!loaded()) {
            return ItemStack.EMPTY;
        }
        return FaunaPediaJeiPlugin.hoveredItem();
    }

    /**
     * La barre de recherche de JEI a-t-elle le focus clavier ? {@code false} si JEI est absent.
     *
     * <p>Sert à ne pas intercepter une touche que le joueur est en train de saisir.</p>
     */
    public static boolean hasKeyboardFocus() {
        return loaded() && FaunaPediaJeiPlugin.hasKeyboardFocus();
    }

    private static boolean loaded() {
        if (jeiLoaded == null) {
            jeiLoaded = ModList.get().isLoaded("jei");
        }
        return jeiLoaded;
    }

    private JeiHoveredItem() {}
}
