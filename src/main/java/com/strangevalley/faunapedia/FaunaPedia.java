package com.strangevalley.faunapedia;

import com.mojang.logging.LogUtils;
import com.strangevalley.faunapedia.data.ModAttachments;
import com.strangevalley.faunapedia.network.ModNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Classe principale du mod FaunaPedia.
 *
 * <p>Le mod ajoute un système de « FaunaPedia » générique : chaque joueur découvre progressivement
 * les types de mobs qu'il croise (vanilla comme moddés), la progression étant arbitrée côté serveur
 * (source de vérité) puis synchronisée vers le client.</p>
 *
 * <p>L'annotation {@link Mod} enregistre le mod auprès de NeoForge. Le constructeur reçoit par
 * injection le bus d'événements du mod (« mod event bus » = canal réservé aux événements de cycle
 * de vie et d'enregistrement) et le conteneur du mod (utilisé ici pour déclarer la config).</p>
 */
@Mod(FaunaPedia.MODID)
public class FaunaPedia {
    /** Identifiant unique du mod (namespace utilisé pour toutes les ressources et clés de registre). */
    public static final String MODID = "faunapedia";

    /** Logger partagé (journalisation dans la console du jeu). */
    public static final Logger LOGGER = LogUtils.getLogger();

    public FaunaPedia(IEventBus modBus, ModContainer container) {
        // Enregistre les Data Attachments (système NeoForge de données attachées aux entités,
        // remplaçant les anciennes Capabilities dépréciées).
        ModAttachments.ATTACHMENT_TYPES.register(modBus);

        // Enregistre les paquets réseau lorsque NeoForge le demande.
        modBus.addListener(ModNetwork::register);

        // Déclare les deux fichiers de configuration. CLIENT : options d'affichage locales au joueur.
        // SERVER : paramètres de détection, arbitrés par le serveur et synchronisés vers les clients
        // (voir FaunaPediaConfig pour la raison du découpage).
        container.registerConfig(ModConfig.Type.CLIENT, FaunaPediaConfig.CLIENT_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, FaunaPediaConfig.SERVER_SPEC);

        LOGGER.info("[FaunaPedia] Mod initialisé (modid = {}).", MODID);
    }
}
