package com.strangevalley.faunapedia.network;

import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.client.ClientPayloadHandler;
import com.strangevalley.faunapedia.server.DropIndexService;
import com.strangevalley.faunapedia.server.MobInfoService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Déclaration des paquets réseau du mod.
 *
 * <p>Trois paquets descendants (serveur → client) et un seul montant (client → serveur), ce dernier
 * étant une simple demande d'ouverture de fiche que le serveur valide avant de répondre. Le serveur
 * reste donc la seule source de vérité.</p>
 *
 * <p><b>Note technique :</b> {@link ClientPayloadHandler} contient du code exclusivement client.
 * Il n'est référencé qu'à l'intérieur des lambdas passées à {@code enqueueWork} : la classe n'est
 * donc chargée qu'au moment où un paquet est réellement reçu, ce qui n'arrive jamais sur un serveur
 * dédié. Le mod reste ainsi utilisable sur serveur dédié sans planter au chargement.</p>
 */
public final class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        // .optional() : le mod n'est pas obligatoire des deux côtés. Un serveur avec FaunaPedia
        // n'oblige donc pas ses clients à l'installer, et un client équipé peut rejoindre un serveur
        // qui ne l'a pas — sans être expulsé. Chaque envoi serveur → client est gardé par
        // hasChannel (voir sendToClient), pour ne rien envoyer à un client qui n'a pas le mod.
        // La version ("1") isole les protocoles incompatibles entre eux.
        PayloadRegistrar registrar = event.registrar("1").optional();

        // --- Serveur → client -------------------------------------------------------------------
        registrar.playToClient(
                SyncFaunaPediaPayload.TYPE,
                SyncFaunaPediaPayload.STREAM_CODEC,
                // enqueueWork : exécute le traitement sur le thread principal du client
                // (obligatoire, car on touche à l'interface graphique).
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleSync(payload))
        );

        registrar.playToClient(
                DiscoverEntityPayload.TYPE,
                DiscoverEntityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleDiscover(payload))
        );

        registrar.playToClient(
                MobInfoPayload.TYPE,
                MobInfoPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMobInfo(payload))
        );

        registrar.playToClient(
                DropIndexPayload.TYPE,
                DropIndexPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleDropIndex(payload))
        );

        // --- Client → serveur -------------------------------------------------------------------
        // Demande d'ouverture d'une fiche. Le serveur vérifie que le mob a bien été découvert
        // avant de répondre (voir MobInfoService).
        registrar.playToServer(
                RequestMobInfoPayload.TYPE,
                RequestMobInfoPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MobInfoService.respond(player, payload.entityId());
                    }
                })
        );

        // Demande de l'index des butins. Le serveur n'y met que les mobs découverts par ce joueur.
        registrar.playToServer(
                RequestDropIndexPayload.TYPE,
                RequestDropIndexPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DropIndexService.respond(player);
                    }
                })
        );

        FaunaPedia.LOGGER.debug("[FaunaPedia] Paquets réseau enregistrés.");
    }

    /**
     * Envoie un paquet à un joueur <b>uniquement</b> si sa connexion connaît ce canal.
     *
     * <p>Indispensable avec des paquets optionnels : un client sans le mod n'a pas enregistré le
     * canal, et lui envoyer quand même lèverait une erreur côté serveur. On l'ignore alors
     * silencieusement — sa progression reste enregistrée côté serveur et lui parviendra s'il
     * installe le mod plus tard.</p>
     */
    public static void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private ModNetwork() {}
}
