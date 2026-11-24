package com.strangevalley.faunapedia.client;

import com.strangevalley.faunapedia.FaunaPedia;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Événements du « mod event bus » côté client (enregistrements effectués au chargement).
 *
 * <p>{@code value = Dist.CLIENT} garantit que cette classe n'est chargée que sur le client :
 * un serveur dédié l'ignore complètement.</p>
 *
 * <p>Le bus n'est pas précisé : NeoForge le déduit désormais du type de l'événement
 * ({@code RegisterKeyMappingsEvent} est un événement de chargement, donc routé vers le mod bus).</p>
 */
@EventBusSubscriber(modid = FaunaPedia.MODID, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FaunaPediaKeyMappings.OPEN_FAUNAPEDIA);
    }

    private ClientModEvents() {}
}
