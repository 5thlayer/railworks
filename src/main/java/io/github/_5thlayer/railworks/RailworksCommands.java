// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github._5thlayer.groundworks.Refusal;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /railworks} commands, for operators: what a Gallery pack scene sets the Rail and its
 * cars up with, since no vanilla command strings a Span or makes a Train (libworks ADR 0010).
 *
 * <ul>
 *   <li>{@code /railworks span <from> <to>} strings a Span between the Arms of the Pylon columns
 *       standing at {@code from} and {@code to}, for no Rail, under the length, grade and bend
 *       limits a player strings by.</li>
 *   <li>{@code /railworks car (locomotive|cargo_car) <pos> <yaw>} sets a car on the Rail where one
 *       riding it would stand in the block {@code pos}, facing {@code yaw} along it, as the car in
 *       a player's hand would: coupled into a Train at rest when that is where it lands beside one
 *       of the Train's ends, and refused where the item would be.</li>
 * </ul>
 *
 * <p>A refusal is an error, as a vanilla command's is, so a function's trace names it.
 */
public final class RailworksCommands {

    private static final DynamicCommandExceptionType NOT_A_PYLON =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.railworks.span.not_a_pylon", pos));
    private static final SimpleCommandExceptionType SAME_ARM =
            new SimpleCommandExceptionType(Component.translatable("commands.railworks.span.same_arm"));
    private static final SimpleCommandExceptionType ALREADY_STRUNG =
            new SimpleCommandExceptionType(Component.translatable("commands.railworks.span.already_strung"));
    private static final DynamicCommandExceptionType SPAN_REFUSED =
            new DynamicCommandExceptionType(reason -> (Component) reason);
    private static final DynamicCommandExceptionType CAR_REFUSED =
            new DynamicCommandExceptionType(key -> Component.translatable((String) key));
    private static final SimpleCommandExceptionType CAR_NOT_SET =
            new SimpleCommandExceptionType(Component.translatable("commands.railworks.car.not_set"));

    private RailworksCommands() {
    }

    public static void register() {
        // RegisterCommandsEvent is a game-bus event, so it does not go through the mod bus.
        NeoForge.EVENT_BUS.addListener(RailworksCommands::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        var car = Commands.literal("car");
        for (CarKind kind : CarKind.values()) {
            car.then(Commands.literal(kind.model())
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180.0F, 360.0F))
                                    .executes(context -> car(context, kind)))));
        }
        event.getDispatcher().register(Commands.literal(Railworks.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("span")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(RailworksCommands::span))))
                .then(car));
    }

    private static int span(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        for (BlockPos pos : new BlockPos[] {from, to}) {
            if (!PylonColumn.isPylon(level, pos)) {
                throw NOT_A_PYLON.create(pos.toShortString());
            }
        }
        BlockPos anchor = PylonColumn.topOf(level, from);
        BlockPos target = PylonColumn.topOf(level, to);
        Stringing.Outcome outcome = LevelRails.of(level).string(level, anchor, target);
        switch (outcome.result()) {
            case STRUNG -> {
                context.getSource().sendSuccess(() -> Component.translatable("commands.railworks.span.strung",
                        anchor.toShortString(), target.toShortString()), true);
                return 1;
            }
            case CANCELLED -> throw SAME_ARM.create();
            case CUT -> throw ALREADY_STRUNG.create();
            default -> throw SPAN_REFUSED.create(SpanGesture.reason(outcome.result(),
                    PylonColumn.arm(anchor), PylonColumn.arm(target)));
        }
    }

    private static int car(CommandContext<CommandSourceStack> context, CarKind kind) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        float yaw = FloatArgumentType.getFloat(context, "yaw");
        LevelRails rails = LevelRails.of(level);
        var result = CarPlacement.plan(level, rails.network(), CarPlacement.Sight.over(pos, yaw), pos, kind);
        if (result.plan().isRefused()) {
            throw CAR_REFUSED.create(refusalKey(result.plan().refusal()));
        }
        if (CarItem.setOnRail(level, result, kind, context.getSource().getEntity()) == null) {
            throw CAR_NOT_SET.create();
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.railworks.car.set",
                Component.translatable(kind.entityType().getDescriptionId()), pos.toShortString()), true);
        return 1;
    }

    /** The message for why {@link CarPlacement} refused a car. */
    private static String refusalKey(Refusal refusal) {
        if (refusal == RailworksRefusal.OFF_SPAN) {
            return "commands.railworks.car.off_span";
        }
        if (refusal == RailworksRefusal.SPAN_TAKEN) {
            return "commands.railworks.car.span_taken";
        }
        if (refusal == RailworksRefusal.BLOCKED_CAR) {
            return "commands.railworks.car.blocked_car";
        }
        return "commands.railworks.car.not_set";
    }
}
