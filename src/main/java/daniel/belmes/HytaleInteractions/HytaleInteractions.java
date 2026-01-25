package daniel.belmes.HytaleInteractions;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.registry.CodecMapRegistry;

import daniel.belmes.HytaleInteractions.interactions.BlockRadiusInteraction;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;

public class HytaleInteractions extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HytaleInteractions(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        LOGGER.atInfo().log("HytaleInteractions has started registering interactions!");
        CodecMapRegistry.Assets<Interaction, ? extends Codec<? extends Interaction>> interactionRegistry = this
                .getCodecRegistry(Interaction.CODEC);
        interactionRegistry.register("BlockRadius", BlockRadiusInteraction.class, BlockRadiusInteraction.CODEC);
        LOGGER.atInfo().log("HytaleInteractions has finished registering interactions!");
    }
}
