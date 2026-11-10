package de.justgino.plugins.wildstore;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class Purchases implements Listener {

    private record Wave(String buyer, long until, Set<UUID> rewarded) {
    }

    private final WildStore plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, ScheduledTask> pending = new ConcurrentHashMap<>();
    private final Map<String, Wave> waves = new ConcurrentHashMap<>();

    public Purchases(WildStore plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public void purchase(String player) {
        long debounce = Math.max(0, config().getLong("purchase.debounce", 2));
        String key = player.toLowerCase(Locale.ROOT);
        pending.compute(key, (name, running) -> {
            if (running != null) running.cancel();
            return Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
                pending.remove(name, task);
                announce(player);
            }, Math.max(1, debounce * 1000), TimeUnit.MILLISECONDS);
        });
    }

    private void announce(String player) {
        String[] head = config().getBoolean("purchase.head", true) ? head(player) : null;
        String link = config().getString("store.link", "");
        List<String> lines = plugin.texts().lines("purchase");
        int rows = head != null ? Math.max(8, lines.size()) : lines.size();
        List<String> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            String text = i < lines.size() ? lines.get(i) : "";
            String pixels = head != null && i < 8 ? head[i] + "<reset>" : "";
            out.add(pixels + text);
        }
        if (config().getBoolean("gg-wave.enabled", true)) {
            long until = System.currentTimeMillis() + Math.max(1, config().getLong("gg-wave.seconds", 15)) * 1000L;
            waves.put(player.toLowerCase(Locale.ROOT), new Wave(player, until, ConcurrentHashMap.newKeySet()));
        }
        Sound sound = sound(config().getString("purchase.sound", ""),
                (float) config().getDouble("purchase.volume", 1.0), (float) config().getDouble("purchase.pitch", 1.0));
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (String line : out) Bukkit.getServer().sendMessage(Texts.parse(line, "player", player, "link", link));
            if (sound != null) for (Player online : Bukkit.getOnlinePlayers()) online.playSound(sound);
        });
    }

    private String[] head(String player) {
        try {
            String url = config().getString("purchase.head-url", "https://minotar.net/helm/{player}/8.png").replace("{player}", player);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage image = response.statusCode() == 200 ? ImageIO.read(new ByteArrayInputStream(response.body())) : null;
            if (image != null && image.getWidth() >= 8 && image.getHeight() >= 8) {
                String[] rows = new String[8];
                for (int y = 0; y < 8; y++) {
                    StringBuilder row = new StringBuilder();
                    for (int x = 0; x < 8; x++) {
                        int rgb = image.getRGB(x * image.getWidth() / 8, y * image.getHeight() / 8) & 0xFFFFFF;
                        row.append("<#").append(String.format("%06x", rgb)).append(">█");
                    }
                    rows[y] = row.toString();
                }
                return rows;
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        String[] gray = new String[8];
        java.util.Arrays.fill(gray, "<dark_gray>████████");
        return gray;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (waves.isEmpty() || !config().getBoolean("gg-wave.enabled", true)) return;
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).toLowerCase(Locale.ROOT);
        if (!isGg(message)) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        waves.values().removeIf(wave -> wave.until() < now);
        for (Wave wave : waves.values()) {
            if (!config().getBoolean("gg-wave.reward-buyer", false) && wave.buyer().equalsIgnoreCase(player.getName())) continue;
            if (wave.rewarded().add(player.getUniqueId())) reward(player, wave.buyer());
        }
    }

    private boolean isGg(String message) {
        for (String keyword : config().getStringList("gg-wave.keywords")) {
            String word = keyword.trim().toLowerCase(Locale.ROOT);
            if (word.isEmpty()) continue;
            if (Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(word) + "($|[^\\p{L}\\p{N}])").matcher(message).find()) return true;
        }
        return false;
    }

    private void reward(Player player, String buyer) {
        Sound sound = sound(config().getString("gg-wave.sound", ""), 1f, 1f);
        player.getScheduler().run(plugin, task -> {
            Object[] ph = {"player", player.getName(), "buyer", buyer};
            String actionbar = plugin.texts().raw("gg.actionbar");
            if (!actionbar.isEmpty()) player.sendActionBar(Texts.parse(actionbar, ph));
            String chat = plugin.texts().raw("gg.chat");
            if (!chat.isEmpty()) player.sendMessage(Texts.parse(chat, ph));
            if (sound != null) player.playSound(sound);
        }, null);
        List<String> commands = config().getStringList("gg-wave.commands");
        if (commands.isEmpty()) return;
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (String command : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()).replace("{buyer}", buyer));
            }
        });
    }

    private Sound sound(String name, float volume, float pitch) {
        if (name == null || name.isBlank()) return null;
        try {
            return Sound.sound(Key.key(name.trim().toLowerCase(Locale.ROOT)), Sound.Source.MASTER, volume, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Unknown sound in config.yml: " + name);
            return null;
        }
    }

    public void shutdown() {
        pending.values().forEach(ScheduledTask::cancel);
        pending.clear();
        waves.clear();
    }
}
