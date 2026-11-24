package com.strangevalley.faunapedia.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strangevalley.faunapedia.FaunaPedia;
import com.strangevalley.faunapedia.MobCategories.Category;
import com.strangevalley.faunapedia.data.FaunaPediaData;
import com.strangevalley.faunapedia.data.ModAttachments;
import com.strangevalley.faunapedia.network.ModNetwork;
import com.strangevalley.faunapedia.network.SyncFaunaPediaPayload;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Commande {@code /faunapedia} permettant au joueur de débloquer des mobs manuellement.
 *
 * <ul>
 *   <li>{@code /faunapedia unlock <entity>} — débloque un mob précis (avec auto-complétion) ;</li>
 *   <li>{@code /faunapedia unlockall} — débloque tous les mobs répertoriables ;</li>
 *   <li>{@code /faunapedia unlockcategory <catégorie>} — débloque une catégorie entière.</li>
 * </ul>
 *
 * <p><b>Permission :</b> niveau 2 (opérateur). En solo, cela équivaut à « autoriser les commandes ».
 * Débloquer un mob accorde de la progression : c'est une action de type triche, réservée à un joueur
 * privilégié — cohérent avec le fait que le serveur reste la source de vérité.</p>
 *
 * <p><b>Catégories :</b> la commande range les mobs avec le <b>même</b> classificateur commun que le
 * menu ({@code MobCategories}), donc « débloquer tous les Volants » débloque exactement ce que le
 * menu affiche sous « Volants ».</p>
 *
 * <p>Après un déblocage, on renvoie une synchronisation <b>complète</b> (et non des deltas) : cela
 * évite un toast par mob lors d'un déblocage massif ; la confirmation se fait dans le chat.</p>
 */
@EventBusSubscriber(modid = FaunaPedia.MODID)
public final class FaunaPediaCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandBuildContext buildContext = event.getBuildContext();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("faunapedia")
                .requires(source -> source.hasPermission(2));

        // /faunapedia unlock <entity>
        root.then(Commands.literal("unlock")
                .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                        .executes(ctx -> unlockOne(ctx.getSource(),
                                ResourceArgument.getResource(ctx, "entity", Registries.ENTITY_TYPE)))));

        // /faunapedia unlockall
        root.then(Commands.literal("unlockall")
                .executes(ctx -> unlockAll(ctx.getSource())));

        // /faunapedia unlockcategory <hostile|passive|aquatic|flying|other>
        LiteralArgumentBuilder<CommandSourceStack> category = Commands.literal("unlockcategory");
        for (Category cat : Category.values()) {
            category.then(Commands.literal(cat.getCommandName())
                    .executes(ctx -> unlockCategory(ctx.getSource(), cat)));
        }
        root.then(category);

        event.getDispatcher().register(root);
    }

    // --- Exécution ------------------------------------------------------------------------------

    private static int unlockOne(CommandSourceStack source,
                                 Holder.Reference<EntityType<?>> holder) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EntityType<?> type = holder.value();

        int unlocked = unlockTypes(player, List.of(type));
        if (unlocked > 0) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.faunapedia.unlocked_one", type.getDescription()), false);
        } else {
            source.sendFailure(Component.translatable(
                    "commands.faunapedia.already_one", type.getDescription()));
        }
        return unlocked;
    }

    private static int unlockAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int unlocked = unlockTypes(player, ServerMobCatalog.allTypes(player.serverLevel()));
        reportBulk(source, unlocked);
        return unlocked;
    }

    private static int unlockCategory(CommandSourceStack source, Category category) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<EntityType<?>> types = ServerMobCatalog.typesInCategory(player.serverLevel(), category);
        int unlocked = unlockTypes(player, types);
        reportBulk(source, unlocked);
        return unlocked;
    }

    private static void reportBulk(CommandSourceStack source, int unlocked) {
        if (unlocked > 0) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.faunapedia.unlocked_many", unlocked), false);
        } else {
            source.sendFailure(Component.translatable("commands.faunapedia.already_many"));
        }
    }

    /**
     * Débloque une collection de types pour le joueur. Renvoie le nombre de mobs <b>réellement</b>
     * nouveaux (ceux déjà découverts sont ignorés).
     */
    private static int unlockTypes(ServerPlayer player, Collection<EntityType<?>> types) {
        ServerLevel level = player.serverLevel();
        FaunaPediaData data = player.getData(ModAttachments.FAUNAPEDIA.get());

        long day = level.getDayTime() / 24000L;
        long gameTime = level.getGameTime();

        int unlocked = 0;
        for (EntityType<?> type : types) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null || data.isDiscovered(id)) {
                continue;
            }
            // Aucun lieu de rencontre : un déblocage par commande n'est pas une vraie rencontre, le
            // mob n'a été vu nulle part. Enregistrer la position du joueur inventerait un souvenir
            // (« découvert en plaine à tel endroit ») que rien ne justifie ; la fiche omettra donc
            // simplement ces lignes, ce qui est la vérité.
            data.recordEncounter(id, day, gameTime, null, null);
            // Réévalue les succès pour ce mob (complétion, boss, mob moddé…), comme une vraie rencontre.
            FaunaPediaAdvancements.check(player, data, id, type);
            unlocked++;
        }

        if (unlocked > 0) {
            player.setData(ModAttachments.FAUNAPEDIA.get(), data);
            // Synchronisation complète : pas de toast par mob lors d'un déblocage massif.
            ModNetwork.sendToClient(player,
                    new SyncFaunaPediaPayload(new HashSet<>(data.getDiscovered())));
        }
        return unlocked;
    }

    private FaunaPediaCommand() {}
}
