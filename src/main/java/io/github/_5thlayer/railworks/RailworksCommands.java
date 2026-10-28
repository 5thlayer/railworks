// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github._5thlayer.groundworks.Refusal;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The {@code /railworks} commands, for operators: what a Gallery pack scene sets the Rail, its cars
 * and its hatches up with, since no vanilla command strings a Span, makes a Train or reaches what the
 * level keeps of a hatch or a Train's mode (libworks ADR 0010).
 *
 * <ul>
 *   <li>{@code /railworks span <from> <to>} strings a Span between the Arms of the Pylon columns
 *       standing at {@code from} and {@code to}, for no Rail, under the length, grade and bend
 *       limits a player strings by.</li>
 *   <li>{@code /railworks car (locomotive|electric_locomotive|cargo_car) <pos> <yaw>} sets a car on the Rail where one
 *       riding it would stand in the block {@code pos}, facing {@code yaw} along it, as the car in
 *       a player's hand would: coupled into a Train at rest when that is where it lands beside one
 *       of the Train's ends, and refused where the item would be.</li>
 *   <li>{@code /railworks hatch <pos> provider <item> <stacks>} puts {@code stacks} full stacks of
 *       {@code item} in the provider hatch at {@code pos}, refused without room for them all; with
 *       {@code requester}, has the requester hatch there ask for that many, a stack at a time as a
 *       player's uses of the item do, refused past its slots. A hatch set by a command a moment
 *       before counts.</li>
 *   <li>{@code /railworks automatic <locomotive>} switches the Locomotive's Train to automatic, as
 *       its menu's toggle does, so it takes jobs between hatches (ADR 0008).</li>
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

    private static final DynamicCommandExceptionType NOT_A_HATCH =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.railworks.hatch.not_a_hatch", pos));
    private static final DynamicCommandExceptionType WRONG_MODE =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.railworks.hatch.wrong_mode", pos));
    private static final DynamicCommandExceptionType NO_ROOM =
            new DynamicCommandExceptionType(pos -> Component.translatable("commands.railworks.hatch.no_room", pos));
    private static final DynamicCommandExceptionType TOO_MANY_STACKS =
            new DynamicCommandExceptionType(slots -> Component.translatable("commands.railworks.hatch.too_many_stacks", slots));
    private static final SimpleCommandExceptionType NOT_A_LOCOMOTIVE =
            new SimpleCommandExceptionType(Component.translatable("commands.railworks.automatic.not_a_locomotive"));

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
        var hatchAt = Commands.argument("pos", BlockPosArgument.blockPos());
        for (HatchMode mode : HatchMode.values()) {
            hatchAt.then(Commands.literal(mode.getSerializedName())
                    .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                            .then(Commands.argument("stacks", IntegerArgumentType.integer(1))
                                    .executes(context -> hatch(context, mode)))));
        }
        event.getDispatcher().register(Commands.literal(Railworks.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("span")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(RailworksCommands::span))))
                .then(car)
                .then(Commands.literal("hatch").then(hatchAt))
                .then(Commands.literal("automatic")
                        .then(Commands.argument("locomotive", EntityArgument.entity())
                                .executes(RailworksCommands::automatic))));
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
            default -> throw SPAN_REFUSED.create(SpanGesture.reason(outcome,
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

    /** @return how many stacks the hatch holds of it, or asks for */
    private static int hatch(CommandContext<CommandSourceStack> context, HatchMode mode) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(level.getBlockEntity(pos) instanceof HatchBlockEntity entity)) {
            throw NOT_A_HATCH.create(pos.toShortString());
        }
        Hatches.Hatch hatch = entity.hatch().orElseThrow(() -> NOT_A_HATCH.create(pos.toShortString()));
        if (hatch.mode() != mode) {
            throw WRONG_MODE.create(pos.toShortString());
        }
        ItemResource resource = ItemResource.of(ItemArgument.getItem(context, "item").item());
        int stacks = IntegerArgumentType.getInteger(context, "stacks");
        int readied;
        if (mode == HatchMode.PROVIDER) {
            int amount = stacks * resource.getMaxStackSize();
            try (var transaction = Transaction.openRoot()) {
                if (hatch.contents().insert(resource, amount, transaction) < amount) {
                    throw NO_ROOM.create(pos.toShortString());
                }
                transaction.commit();
            }
            readied = stacks;
        } else {
            readied = ask(hatch, resource.value(), stacks);
            if (readied < stacks) {
                throw TOO_MANY_STACKS.create(Hatches.slots());
            }
        }
        int told = readied;
        context.getSource().sendSuccess(() -> Component.translatable("commands.railworks.hatch." + mode.getSerializedName(),
                pos.toShortString(), told, resource.getHoverName()), true);
        return readied;
    }

    /**
     * Has the requester {@code hatch} ask for {@code stacks} of {@code item}, afresh, a stack at a time
     * as a player's uses of the item do, so it asks for no more than it has slots.
     *
     * @return how many stacks it asks for
     */
    private static int ask(Hatches.Hatch hatch, Item item, int stacks) {
        hatch.clearRequest();
        int asked = 0;
        for (int stack = 0; stack < stacks; stack++) {
            asked = hatch.ask(item).count();
        }
        return asked;
    }

    private static int automatic(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(context, "locomotive") instanceof LocomotiveEntity locomotive)
                || locomotive.train() == null) {
            throw NOT_A_LOCOMOTIVE.create();
        }
        if (!locomotive.train().automatic()) {
            locomotive.toggleAutomatic();
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.railworks.automatic"), true);
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
