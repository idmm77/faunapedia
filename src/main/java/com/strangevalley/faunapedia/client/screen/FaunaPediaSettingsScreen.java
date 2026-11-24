package com.strangevalley.faunapedia.client.screen;

import com.strangevalley.faunapedia.FaunaPediaConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran de réglages du FaunaPedia, ouvert depuis le bouton « Réglages » du catalogue.
 *
 * <p>N'expose que des options <b>client</b> : ce sont les seules qui appartiennent au joueur. Le
 * rayon et l'intervalle de détection vivent dans la config SERVER et sont arbitrés par le serveur —
 * les proposer ici aurait produit des curseurs sans effet en multijoueur, exactement le défaut que
 * la séparation des configs a corrigé.</p>
 *
 * <p>Tous les réglages sont des {@link CycleButton} : le jeu n'offre pas de widget de saisie adapté
 * à ces valeurs, et un bouton qui fait défiler ses valeurs reprend l'idiome déjà employé par les
 * boutons de tri et de filtre du catalogue.</p>
 */
public class FaunaPediaSettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 220;
    private static final int WIDGET_HEIGHT = 20;

    private final FaunaPediaScreen parent;

    public FaunaPediaSettingsScreen(FaunaPediaScreen parent) {
        super(Component.translatable("faunapedia.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDGET_WIDTH / 2;
        int y = 46;

        // --- Notifications -------------------------------------------------------------------------
        addRenderableWidget(CycleButton.onOffBuilder(FaunaPediaConfig.ENABLE_TOASTS.get())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("faunapedia.settings.toasts"),
                        (button, value) -> set(FaunaPediaConfig.ENABLE_TOASTS, value)));
        y += ROW_HEIGHT;

        addRenderableWidget(CycleButton.onOffBuilder(FaunaPediaConfig.ENABLE_SOUND.get())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("faunapedia.settings.discovery_sound"),
                        (button, value) -> set(FaunaPediaConfig.ENABLE_SOUND, value)));
        y += ROW_HEIGHT;

        addRenderableWidget(new DurationSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT));
        y += ROW_HEIGHT;

        addRenderableWidget(CycleButton.onOffBuilder(FaunaPediaConfig.ENABLE_MOB_SOUNDS.get())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("faunapedia.settings.mob_sounds"),
                        (button, value) -> set(FaunaPediaConfig.ENABLE_MOB_SOUNDS, value)));
        y += ROW_HEIGHT;

        addRenderableWidget(CycleButton.onOffBuilder(FaunaPediaConfig.ENABLE_TOOLTIP_HINT.get())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("faunapedia.settings.tooltip_hint"),
                        (button, value) -> set(FaunaPediaConfig.ENABLE_TOOLTIP_HINT, value)));
        y += ROW_HEIGHT + 8;

        // --- Préférences du catalogue --------------------------------------------------------------
        // Les libellés des valeurs sont ceux des boutons du catalogue : un réglage et le bouton
        // qu'il pilote ne doivent pas s'appeler différemment.
        addRenderableWidget(CycleButton.<String>builder(
                        value -> Component.translatable("faunapedia.sort." + value))
                .withValues("alpha", "mod")
                .withInitialValue(FaunaPediaConfig.DEFAULT_SORT.get())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("faunapedia.settings.default_sort"),
                        (button, value) -> {
                            set(FaunaPediaConfig.DEFAULT_SORT, value);
                            FaunaPediaScreen.applyDefaultsFromConfig();
                        }));
        y += ROW_HEIGHT;

        addRenderableWidget(CycleButton.<String>builder(
                        value -> Component.translatable("faunapedia.filter." + value))
                .withValues("all", "discovered", "undiscovered")
                .withInitialValue(FaunaPediaConfig.DEFAULT_FILTER.get())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("faunapedia.settings.default_filter"),
                        (button, value) -> {
                            set(FaunaPediaConfig.DEFAULT_FILTER, value);
                            FaunaPediaScreen.applyDefaultsFromConfig();
                        }));

        addRenderableWidget(Button.builder(
                        Component.translatable("faunapedia.detail.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    /**
     * Curseur glissant de la durée du toast.
     *
     * <p>La valeur interne d'un {@link AbstractSliderButton} est un réel entre 0 et 1 : on la
     * convertit en secondes entières dans l'intervalle autorisé par la config.</p>
     *
     * <p>La sauvegarde n'a lieu qu'au <b>relâchement</b> du curseur, pas à chaque pixel de
     * glissement : {@code applyValue} est appelé en continu pendant le glissé, et y écrire le
     * fichier produirait des dizaines d'écritures disque pour un seul réglage.</p>
     */
    private class DurationSlider extends AbstractSliderButton {

        private static final int MIN_SECONDS = 3;
        private static final int MAX_SECONDS = 10;

        DurationSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                    (FaunaPediaConfig.TOAST_DURATION_SECONDS.get() - MIN_SECONDS)
                            / (double) (MAX_SECONDS - MIN_SECONDS));
            updateMessage();
        }

        private int seconds() {
            return MIN_SECONDS + (int) Math.round(this.value * (MAX_SECONDS - MIN_SECONDS));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("faunapedia.settings.toast_duration")
                    .append(": ")
                    .append(Component.translatable("faunapedia.settings.seconds", seconds())));
        }

        @Override
        protected void applyValue() {
            FaunaPediaConfig.TOAST_DURATION_SECONDS.set(seconds());
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            FaunaPediaConfig.CLIENT_SPEC.save();
        }
    }

    /**
     * Écrit une valeur et sauvegarde immédiatement le fichier.
     *
     * <p>Sauvegarder à chaque changement plutôt qu'à la fermeture : ces réglages se modifient
     * rarement, et une fermeture brutale du jeu ne doit pas les perdre silencieusement.</p>
     */
    private static <T> void set(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        FaunaPediaConfig.CLIENT_SPEC.save();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFD24B);
    }

    /** Échap remonte au catalogue plutôt que de tout fermer, comme les autres écrans du mod. */
    @Override
    public void onClose() {
        // Filet de sécurité : le curseur se règle aussi au clavier, ce qui ne passe pas par
        // onRelease. Une sauvegarde de plus à la fermeture ne coûte rien et évite un réglage perdu.
        FaunaPediaConfig.CLIENT_SPEC.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
