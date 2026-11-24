package com.strangevalley.faunapedia;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;

/**
 * Configuration du mod (fichiers TOML générés automatiquement par NeoForge).
 *
 * <p>Deux specs distinctes, car les deux familles d'options n'ont ni le même propriétaire ni la même
 * portée :</p>
 *
 * <ul>
 *   <li><b>CLIENT</b> ({@code config/faunapedia-client.toml}) — options d'affichage (toasts, sons).
 *       Elles sont purement locales : chaque joueur règle les siennes, elles n'ont aucun sens côté
 *       serveur et ne sont lues que par du code client.</li>
 *   <li><b>SERVER</b> ({@code <monde>/serverconfig/faunapedia-server.toml}) — paramètres de détection.
 *       Le scan de rencontre tourne côté serveur : c'est donc lui, et lui seul, qui décide. NeoForge
 *       synchronise cette spec vers les clients connectés, si bien qu'une valeur lue côté client est
 *       toujours celle qui s'applique réellement.</li>
 * </ul>
 *
 * <p><b>Pourquoi pas COMMON.</b> Une config COMMON n'est <i>pas</i> synchronisée : sur serveur dédié,
 * client et serveur lisent chacun leur propre fichier. Le rayon de détection y était donc trompeur —
 * un joueur pouvait modifier {@code scanRadius} chez lui sans le moindre effet, le scan s'exécutant
 * avec la valeur du serveur. En solo le défaut restait invisible (un seul processus, un seul fichier).</p>
 */
public final class FaunaPediaConfig {
    /** Spec des options d'affichage, locales à chaque joueur. */
    public static final ModConfigSpec CLIENT_SPEC;
    /** Spec des paramètres de détection, arbitrés par le serveur et synchronisés vers les clients. */
    public static final ModConfigSpec SERVER_SPEC;

    /** Afficher un toast (notification en haut à droite) à la découverte d'un nouveau mob. */
    public static final ModConfigSpec.BooleanValue ENABLE_TOASTS;
    /** Jouer un son à la découverte. */
    public static final ModConfigSpec.BooleanValue ENABLE_SOUND;
    /** Jouer le cri du mob à l'ouverture de sa fiche. */
    public static final ModConfigSpec.BooleanValue ENABLE_MOB_SOUNDS;
    /** Durée d'affichage du toast de découverte, en secondes. */
    public static final ModConfigSpec.IntValue TOAST_DURATION_SECONDS;
    /** Afficher l'astuce « touche du FaunaPedia » dans l'infobulle des objets. */
    public static final ModConfigSpec.BooleanValue ENABLE_TOOLTIP_HINT;
    /** Tri appliqué à l'ouverture du menu : {@code alpha} ou {@code mod}. */
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_SORT;
    /** Filtre appliqué à l'ouverture du menu : {@code all}, {@code discovered} ou {@code undiscovered}. */
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_FILTER;
    /** Rayon de détection, en blocs, autour du joueur. */
    public static final ModConfigSpec.IntValue SCAN_RADIUS;
    /** Intervalle entre deux scans de détection, en ticks (20 ticks = 1 seconde). */
    public static final ModConfigSpec.IntValue SCAN_INTERVAL;

    static {
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("client");
        ENABLE_TOASTS = client
                .comment("Afficher un toast a la decouverte d'un nouveau mob.")
                .define("enableToasts", true);
        ENABLE_SOUND = client
                .comment("Jouer un son a la decouverte d'un nouveau mob.")
                .define("enableDiscoverySound", true);
        ENABLE_MOB_SOUNDS = client
                .comment("Jouer le cri du mob a l'ouverture de sa fiche detaillee.")
                .define("enableMobSounds", true);
        TOAST_DURATION_SECONDS = client
                .comment("Duree d'affichage du toast de decouverte, en secondes.")
                .defineInRange("toastDurationSeconds", 5, 3, 10);
        ENABLE_TOOLTIP_HINT = client
                .comment("Afficher, dans l'infobulle des objets, l'astuce rappelant la touche du FaunaPedia.")
                .define("showTooltipHint", true);
        client.pop();

        client.push("menu");
        // Volontairement stockés en CHAÎNES et non en enums : les modes de tri et de filtre sont des
        // types internes à l'écran (donc client), et cette classe de config est commune — elle est
        // chargée sur serveur dédié, où référencer un type client provoquerait un NoClassDefFoundError.
        // Arrays.asList et NON List.of : lors de la correction d'un fichier auquel il manque la clé,
        // NeoForge teste la valeur absente (null) contre la liste des valeurs acceptées. Les
        // collections immuables de List.of lèvent une NullPointerException sur contains(null) au lieu
        // de renvoyer false, ce qui casse le chargement de toute la config.
        DEFAULT_SORT = client
                .comment("Tri applique a l'ouverture du menu : alpha (A-Z) ou mod (par mod d'origine).")
                .defineInList("defaultSort", "alpha", Arrays.asList("alpha", "mod"));
        DEFAULT_FILTER = client
                .comment("Filtre applique a l'ouverture du menu : all, discovered ou undiscovered.")
                .defineInList("defaultFilter", "all", Arrays.asList("all", "discovered", "undiscovered"));
        client.pop();
        CLIENT_SPEC = client.build();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("detection");
        SCAN_RADIUS = server
                .comment("Rayon (en blocs) autour du joueur dans lequel les mobs sont detectes.")
                .defineInRange("scanRadius", 24, 4, 128);
        SCAN_INTERVAL = server
                .comment("Intervalle (en ticks) entre deux scans de detection. 20 ticks = 1 seconde.")
                .defineInRange("scanIntervalTicks", 20, 5, 200);
        server.pop();
        SERVER_SPEC = server.build();
    }

    private FaunaPediaConfig() {}
}
