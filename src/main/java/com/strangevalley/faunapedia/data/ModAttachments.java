package com.strangevalley.faunapedia.data;

import com.strangevalley.faunapedia.FaunaPedia;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Enregistrement des Data Attachments du mod.
 *
 * <p>Un « Data Attachment » est le mécanisme moderne de NeoForge pour accrocher des données
 * personnalisées à une entité, un chunk ou un item (il remplace les anciennes « Capabilities »,
 * dépréciées). Ici on accroche un {@link FaunaPediaData} à chaque joueur.</p>
 */
public final class ModAttachments {
    /**
     * Registre différé (« DeferredRegister » = liste d'objets à enregistrer au bon moment
     * du chargement, plutôt que tout de suite).
     */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FaunaPedia.MODID);

    /**
     * Attachment portant la progression du FaunaPedia d'un joueur.
     *
     * <ul>
     *   <li>{@code serialize(CODEC)} : rend les données persistantes (sauvegardées avec le joueur).</li>
     *   <li>{@code copyOnDeath()} : la progression est conservée à la mort/réapparition du joueur.</li>
     * </ul>
     */
    public static final Supplier<AttachmentType<FaunaPediaData>> FAUNAPEDIA =
            ATTACHMENT_TYPES.register("faunapedia_data", () ->
                    // Lambda explicite plutôt que FaunaPediaData::new : la référence de constructeur
                    // serait ambiguë entre les surcharges builder(Supplier) et
                    // builder(Function<IAttachmentHolder, …>), toutes deux compatibles en arité.
                    AttachmentType.builder(() -> new FaunaPediaData())
                            .serialize(FaunaPediaData.CODEC)
                            .copyOnDeath()
                            .build());

    private ModAttachments() {}
}
