package com.strangevalley.faunapedia.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Barre de défilement verticale, cliquable et glissable.
 *
 * <p>Utilitaire partagé par les écrans du mod : la logique est identique pour la liste des mobs et
 * pour celle des biomes, la dupliquer inviterait les deux copies à diverger.</p>
 */
final class ScrollBar {

    /** Largeur de la barre, en pixels. */
    private static final int WIDTH = 4;
    /** Hauteur minimale du curseur, pour qu'il reste attrapable même sur une très longue liste. */
    private static final int MIN_THUMB_HEIGHT = 16;

    /**
     * Dessine la barre. Ne dessine rien si tout le contenu tient à l'écran.
     *
     * @param x            bord gauche de la barre
     * @param top          haut de la zone de défilement
     * @param bottom       bas de la zone de défilement
     * @param scroll       position de défilement courante
     * @param maxScroll    défilement maximal
     * @param contentHeight hauteur totale du contenu
     */
    static void render(GuiGraphics graphics, int x, int top, int bottom,
                       double scroll, double maxScroll, int contentHeight) {
        if (maxScroll <= 0 || contentHeight <= 0) {
            return;
        }

        int trackHeight = bottom - top;
        graphics.fill(x, top, x + WIDTH, bottom, 0x40FFFFFF);

        // Hauteur du curseur proportionnelle à la part visible du contenu.
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT,
                (int) (trackHeight * (trackHeight / (double) contentHeight)));
        int thumbTop = top + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll));

        graphics.fill(x, thumbTop, x + WIDTH, thumbTop + thumbHeight, 0xFFAAAAAA);
    }

    /**
     * Traduit un clic en position de défilement.
     *
     * @return la nouvelle position, ou {@code null} si le clic n'était pas sur la barre.
     */
    static Double scrollFromClick(double mouseX, double mouseY, int x, int top, int bottom,
                                  double maxScroll) {
        if (maxScroll <= 0) {
            return null;
        }
        boolean onBar = mouseX >= x && mouseX <= x + WIDTH && mouseY >= top && mouseY <= bottom;
        return onBar ? scrollFromPosition(mouseY, top, bottom, maxScroll) : null;
    }

    /**
     * Traduit une position verticale du curseur en position de défilement.
     *
     * <p>Utilisé pendant un glissement : la position horizontale n'est volontairement pas testée,
     * pour que le défilement continue de suivre la souris même si elle sort latéralement de la
     * barre — c'est le comportement attendu d'un glissement.</p>
     */
    static double scrollFromPosition(double mouseY, int top, int bottom, double maxScroll) {
        int trackHeight = bottom - top;
        if (trackHeight <= 0) {
            return 0.0D;
        }
        double fraction = (mouseY - top) / trackHeight;
        return Mth.clamp(fraction * maxScroll, 0.0D, maxScroll);
    }

    private ScrollBar() {}
}
