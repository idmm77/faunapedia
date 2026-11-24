package com.strangevalley.faunapedia.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Touches du mod.
 *
 * <p>Le raccourci apparaît dans Options → Commandes, catégorie « FaunaPedia », et reste
 * entièrement réassignable par le joueur.</p>
 */
public final class FaunaPediaKeyMappings {

    /** Ouvre le menu collection. Touche par défaut : P (libre en vanilla). */
    public static final KeyMapping OPEN_FAUNAPEDIA = new KeyMapping(
            "key.faunapedia.open",                 // clé de traduction du nom de la touche
            KeyConflictContext.IN_GAME,         // n'est active qu'en jeu, pas dans les menus
            InputConstants.Type.KEYSYM,         // touche clavier (par opposition à un bouton souris)
            GLFW.GLFW_KEY_P,
            "key.categories.faunapedia"            // clé de traduction de la catégorie
    );

    private FaunaPediaKeyMappings() {}
}
