package com.interactivedisplay.command;

import com.interactivedisplay.core.positioning.PositionMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.function.Predicate;
import net.minecraft.util.math.Vec3d;

public final class InteractiveDisplayCommandTree {
    private InteractiveDisplayCommandTree() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher,
                                    Handlers<S> handlers,
                                    Predicate<S> canCreate,
                                    Predicate<S> canRemove,
                                    Predicate<S> canReload,
                                    Predicate<S> canList,
                                    Predicate<S> canDebug,
                                    SuggestionProvider<S> playerSuggestions,
                                    SuggestionProvider<S> windowSuggestions) {
        dispatcher.register(buildTree(handlers, canCreate, canRemove, canReload, canList, canDebug, playerSuggestions, windowSuggestions));
    }

    private static <S> LiteralArgumentBuilder<S> buildTree(Handlers<S> handlers,
                                                           Predicate<S> canCreate,
                                                           Predicate<S> canRemove,
                                                           Predicate<S> canReload,
                                                           Predicate<S> canList,
                                                           Predicate<S> canDebug,
                                                           SuggestionProvider<S> playerSuggestions,
                                                           SuggestionProvider<S> windowSuggestions) {
        return LiteralArgumentBuilder.<S>literal("interactivedisplay")
                .then(LiteralArgumentBuilder.<S>literal("create")
                        .requires(canCreate)
                        .then(RequiredArgumentBuilder.<S, String>argument("windowId", StringArgumentType.word())
                                .suggests(windowSuggestions)
                                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                        .suggests(playerSuggestions)
                                        .then(createLiteral(handlers, PositionMode.FIXED, "fixed"))
                                        .then(createLiteral(handlers, PositionMode.PLAYER_FIXED, "player_fixed"))
                                        .then(createLiteral(handlers, PositionMode.PLAYER_VIEW, "player_view")))))
                .then(LiteralArgumentBuilder.<S>literal("remove")
                        .requires(canRemove)
                        .then(RequiredArgumentBuilder.<S, String>argument("windowId", StringArgumentType.word())
                                .suggests(windowSuggestions)
                                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                        .suggests(playerSuggestions)
                                        .executes(context -> handlers.remove(context, argWindowId(context), argPlayer(context))))))
                .then(LiteralArgumentBuilder.<S>literal("reload")
                        .requires(canReload)
                        .executes(context -> handlers.reload(context, null))
                        .then(RequiredArgumentBuilder.<S, String>argument("windowId", StringArgumentType.word())
                                .suggests(windowSuggestions)
                                .executes(context -> handlers.reload(context, argWindowId(context)))))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .requires(canList)
                        .executes(handlers::list))
                .then(LiteralArgumentBuilder.<S>literal("debug")
                        .requires(canDebug)
                        .then(LiteralArgumentBuilder.<S>literal("status")
                                .executes(handlers::debugStatus))
                        .then(LiteralArgumentBuilder.<S>literal("recent")
                                .executes(context -> handlers.debugRecent(context, null))
                                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                        .suggests(playerSuggestions)
                                        .executes(context -> handlers.debugRecent(context, argPlayer(context)))))
                        .then(LiteralArgumentBuilder.<S>literal("window")
                                .then(RequiredArgumentBuilder.<S, String>argument("windowId", StringArgumentType.word())
                                        .suggests(windowSuggestions)
                                        .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                                .suggests(playerSuggestions)
                                                .executes(context -> handlers.debugWindow(context, argWindowId(context), argPlayer(context))))))
                        .then(LiteralArgumentBuilder.<S>literal("bindings")
                                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                        .suggests(playerSuggestions)
                                        .executes(context -> handlers.debugBindings(context, argPlayer(context))))));
    }

    private static <S> LiteralArgumentBuilder<S> createLiteral(Handlers<S> handlers, PositionMode mode, String literal) {
        LiteralArgumentBuilder<S> node = LiteralArgumentBuilder.<S>literal(literal)
                .executes(context -> handlers.create(context, argWindowId(context), argPlayer(context), mode, null));
        if (mode == PositionMode.FIXED) {
            node.then(RequiredArgumentBuilder.<S, Double>argument("x", DoubleArgumentType.doubleArg())
                    .then(RequiredArgumentBuilder.<S, Double>argument("y", DoubleArgumentType.doubleArg())
                            .then(RequiredArgumentBuilder.<S, Double>argument("z", DoubleArgumentType.doubleArg())
                                    .executes(context -> handlers.create(context, argWindowId(context), argPlayer(context), mode, argPosition(context))))));
        }
        return node;
    }

    private static <S> String argWindowId(CommandContext<S> context) {
        return StringArgumentType.getString(context, "windowId");
    }

    private static <S> String argPlayer(CommandContext<S> context) {
        return StringArgumentType.getString(context, "player");
    }

    private static <S> Vec3d argPosition(CommandContext<S> context) {
        return new Vec3d(
                DoubleArgumentType.getDouble(context, "x"),
                DoubleArgumentType.getDouble(context, "y"),
                DoubleArgumentType.getDouble(context, "z")
        );
    }

    public interface Handlers<S> {
        int create(CommandContext<S> context, String windowId, String playerName, PositionMode positionMode, Vec3d position) throws CommandSyntaxException;

        int remove(CommandContext<S> context, String windowId, String playerName) throws CommandSyntaxException;

        int reload(CommandContext<S> context, String windowId) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int debugStatus(CommandContext<S> context) throws CommandSyntaxException;

        int debugRecent(CommandContext<S> context, String playerName) throws CommandSyntaxException;

        int debugWindow(CommandContext<S> context, String windowId, String playerName) throws CommandSyntaxException;

        int debugBindings(CommandContext<S> context, String playerName) throws CommandSyntaxException;
    }
}
