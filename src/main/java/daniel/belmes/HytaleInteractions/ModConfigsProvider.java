package daniel.belmes.HytaleInteractions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;

public class ModConfigsProvider {
    private static final Map<String, Boolean> CACHE;
    private static final ModConfigsProvider PROVIDER;
    static {
        CACHE = new ConcurrentHashMap<>();
        PROVIDER = new ModConfigsProvider();
    }

    private ModConfigsProvider() {}

    public static ModConfigsProvider getProvider() {
        return PROVIDER;
    }

    public static Boolean get(String modName, String variable) {
        return CACHE.computeIfAbsent(modName + variable, key -> loadConfigAndGetValue(modName, variable));
    }

    private static Boolean loadConfigAndGetValue(String modName, String variable) {
        Path configDir = Path.of("mods", modName);
        Path configFile = configDir.resolve("config.json");
        try {
            if (Files.exists(configFile)) {
                BsonDocument document = BsonUtil.readDocumentNow(configFile);
                if (document != null) {
                    BsonBoolean result = document.getBoolean(variable);
                    return result.getValue();
                }
            } else {
                save(configDir, configFile);
                HytaleLogger.getLogger().atInfo().log("Created empty config for mod: " + modName);
                return false;
            }
        } catch (Exception e) {
            HytaleLogger.getLogger().atWarning().withCause(e).log("Failed to load Config for" + configDir.toString());
            return false;
        }
        return false;
    }

    private static void save(Path configDir, Path configFile) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(configFile, "{\n}");
        } catch (IOException e) {
            HytaleLogger.getLogger().atWarning().withCause(e).log("Failed to save config");
        }
    }

}
