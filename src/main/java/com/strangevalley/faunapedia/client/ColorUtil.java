package com.strangevalley.faunapedia.client;

/**
 * Petites opérations de couleur partagées par l'interface.
 *
 * <p>Extrait du toast, qui en était le premier utilisateur, dès qu'un deuxième écran a eu besoin
 * de teinter des éléments vers la couleur d'accent d'une catégorie.</p>
 */
public final class ColorUtil {

    /**
     * Interpole deux couleurs, composante par composante. Le résultat est opaque.
     *
     * @param ratio 0 = {@code from} inchangée, 1 = {@code to}
     */
    public static int mix(int from, int to, float ratio) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * ratio);
        int g = Math.round(fg + (tg - fg) * ratio);
        int b = Math.round(fb + (tb - fb) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Reprend le RVB d'une couleur en lui imposant une transparence donnée (0-255). */
    public static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private ColorUtil() {}
}
