package de.justgino.plugins.wildstore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Texts {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final String file;
    private YamlConfiguration texts = new YamlConfiguration();

    public Texts(JavaPlugin plugin, String file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void load() {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), file));
        try (InputStream in = plugin.getResource(file)) {
            if (in != null) loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
        }
        texts = loaded;
    }

    public String raw(String path) {
        return texts.getString(path, "");
    }

    public List<String> lines(String path) {
        if (texts.isList(path)) return texts.getStringList(path);
        String single = texts.getString(path, "");
        return single.isEmpty() ? List.of() : List.of(single);
    }

    public Component get(String path, Object... placeholders) {
        return parse(raw(path), placeholders);
    }

    public void send(CommandSender to, String path, Object... placeholders) {
        String text = raw(path);
        if (!text.isEmpty()) to.sendMessage(parse(raw("prefix") + text, placeholders));
    }

    public void sendLines(CommandSender to, String path, Object... placeholders) {
        for (String line : lines(path)) to.sendMessage(parse(line, placeholders));
    }

    public static Component parse(String raw, Object... placeholders) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String text = raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", MINI.escapeTags(String.valueOf(placeholders[i + 1])));
        }
        return MINI.deserialize(text);
    }
}
