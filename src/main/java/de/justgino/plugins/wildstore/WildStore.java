package de.justgino.plugins.wildstore;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class WildStore extends JavaPlugin {

    private Texts texts;
    private Purchases purchases;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "lang.yml").exists()) saveResource("lang.yml", false);
        texts = new Texts(this, "lang.yml");
        texts.load();
        purchases = new Purchases(this);
        getServer().getPluginManager().registerEvents(purchases, this);
        List<String> aliases = getConfig().getStringList("store.aliases");
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(Commands.literal("store")
                    .requires(source -> source.getSender().hasPermission("wildstore.store"))
                    .executes(ctx -> {
                        texts.sendLines(ctx.getSource().getSender(), "store", "link", getConfig().getString("store.link", ""));
                        return Command.SINGLE_SUCCESS;
                    }).build(), "Shows the store link", aliases);
            registrar.register(Commands.literal("storepurchase")
                    .requires(source -> source.getSender().hasPermission("wildstore.purchase"))
                    .executes(ctx -> {
                        texts.send(ctx.getSource().getSender(), "messages.usage-purchase");
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                String typed = builder.getRemainingLowerCase();
                                for (Player online : Bukkit.getOnlinePlayers()) {
                                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(online.getName());
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String player = StringArgumentType.getString(ctx, "player");
                                purchases.purchase(player);
                                texts.send(ctx.getSource().getSender(), "messages.announced", "player", player);
                                return Command.SINGLE_SUCCESS;
                            })).build(), "Announces a store purchase", List.of());
            registrar.register(Commands.literal("wildstore")
                    .requires(source -> source.getSender().hasPermission("wildstore.reload"))
                    .then(Commands.literal("reload").executes(ctx -> {
                        reloadConfig();
                        texts.load();
                        texts.send(ctx.getSource().getSender(), "messages.reloaded");
                        return Command.SINGLE_SUCCESS;
                    })).build(), "WildStore admin", List.of());
        });
    }

    @Override
    public void onDisable() {
        if (purchases != null) purchases.shutdown();
    }

    public Texts texts() {
        return texts;
    }
}
