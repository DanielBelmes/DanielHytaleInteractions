package daniel.belmes.HytaleInteractions.interactions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.codec.ProtocolCodecs;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.meta.DynamicMetaStore;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.util.BsonUtil;

import daniel.belmes.HytaleInteractions.ModConfigsProvider;

public class ConfigInteraction extends SimpleInteraction {
    public static final MetaKey<Boolean> BOOL_VAL = META_REGISTRY.registerMetaObject(data -> false);
    public static final BuilderCodec<ConfigInteraction> CODEC = BuilderCodec.builder(
            ConfigInteraction.class, ConfigInteraction::new, SimpleInteraction.CODEC)
            .documentation("An interaction that is successful if the given config variable is true.")
            .appendInherited(
                    new KeyedCodec<>("ModFolder", Codec.STRING),
                    (interaction, s) -> interaction.modFolder = s,
                    interaction -> interaction.modFolder,
                    (interaction, parent) -> interaction.modFolder = parent.modFolder)
            .documentation("Override ModFolder to read incase Interaction is not used on a held item")
            .add()
            .appendInherited(
                    new KeyedCodec<>("ConfigVariable", Codec.STRING, true),
                    (interaction, s) -> interaction.configVariable = s,
                    interaction -> interaction.configVariable,
                    (interaction, parent) -> interaction.configVariable = parent.configVariable)
            .add()
            .build();

    private String modFolder;
    private String configVariable;

    protected Boolean loadConfigAndGetValue(Path configDir, String variable) {
        Path configFile = configDir.resolve("config.json");
        HytaleLogger.getLogger().atInfo().log("Config File: " + configFile.toString());
        try {
            if (Files.exists(configFile)) {
                BsonDocument document = BsonUtil.readDocumentNow(configFile);
                if (document != null) {
                    BsonBoolean result = document.getBoolean(variable);
                    return result.getValue();
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            HytaleLogger.getLogger().atSevere().withCause(e).log("Failed to load Config for" + configDir.toString());
            return false;
        }
        return false;
    }

    protected String escapeFilePath (String str) {
        return str.replace(":","_").replace("*","_").replace("/","_").replace("\\","_").replace("\"","_").replace("|","_").replace("?","_").replace("<","_").replace(">","_");
    }

    protected void tick0(
            boolean firstRun, float time, InteractionType type, InteractionContext context,
            CooldownHandler cooldownHandler) {
        ItemStack itemStack = context.getHeldItem();
        if (!Interaction.failed(context.getState().state)) {
            if (firstRun) {
                if (itemStack == null) {
                    context.getState().state = InteractionState.Failed;
                } else {
                    Item item = itemStack.getItem();
                    if (item == null) {
                        context.getState().state = InteractionState.Failed;
                    } else {
                        String ModIdentifier;
                        if(modFolder != null) {
                            ModIdentifier = escapeFilePath(modFolder);
                        } else {
                            ModIdentifier = escapeFilePath(item.getAssetStore().getAssetMap().getAssetPack(item.getId()));
                        }
                        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
                        Path configDir = Path.of("mods", ModIdentifier);
                        DynamicMetaStore<Interaction> instanceStore = context.getInstanceStore();
                        boolean value = ModConfigsProvider.get(ModIdentifier, configVariable);
                        if (value) {
                            context.getState().state = InteractionState.Finished;
                        } else {
                            context.getState().state = InteractionState.Failed;
                        }
                    }
                }
            }
        }
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }

}
