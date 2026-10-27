// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchBlockEntity;
import io.github._5thlayer.railworks.HatchItem;
import io.github._5thlayer.railworks.Hatches;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.HatchReport;
import io.github._5thlayer.railworks.LevelHatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.rail.HatchRequest;
import io.github._5thlayer.railworks.rail.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Hatches join a Station's platform (#36, ADR 0008): a Station north of {@link LocomotiveTests}'
 * Span, which runs east from x 1 to x 9 along z 3, at x 3 serves Trains heading west, so its platform
 * runs back east from it: the leading car's place beside x 3 and 4, its first car spot beside x 5 and
 * 6 and its second beside x 7 and 8, either side of the Rail at a car's floor, y 2. A hatch places beside a car spot and nowhere else, keeps its mode, request and
 * contents in the level's {@link LevelHatches}, is reached by hoppers through its block, and joins
 * the Station as long as its platform covers it. Which block is beside which spot is
 * {@code PlatformTest}'s. All share the {@code logistics_hatch_} prefix.
 */
final class HatchTests {

    /** Where the Station stands, beside the Rail a block below a car's floor, north of it. */
    private static final BlockPos STATION = new BlockPos(3, 1, 2);
    /** Beside the leading car's place, which has no car spot. */
    private static final BlockPos LEADER = new BlockPos(4, 2, 2);
    /** Beside the first car spot at a car's floor, over the block behind the Station. */
    private static final BlockPos FIRST = new BlockPos(6, 2, 2);
    /** Beside the first car spot across the Rail. */
    private static final BlockPos ACROSS = new BlockPos(5, 2, 4);
    /** Beside the second car spot. */
    private static final BlockPos SECOND = new BlockPos(7, 2, 2);
    /** Ahead of the Station, beside no car spot. */
    private static final BlockPos AHEAD = new BlockPos(4, 2, 2);

    private HatchTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("logistics_hatch_crafts_from_iron_round_a_chest_and_a_hopper", 20, HatchTests::crafts);
        tests.test("logistics_hatch_places_beside_a_car_spot_and_joins_its_station", 20, HatchTests::places);
        tests.test("logistics_hatch_is_refused_beside_no_car_spot_and_beside_the_leading_car", 20, HatchTests::refused);
        tests.test("logistics_hatch_free_car_spots_are_drawn_while_one_is_held", 20, HatchTests::freeSpots);
        tests.test("logistics_hatch_switches_in_the_air_and_placed_keeping_what_it_holds", 20, HatchTests::switches);
        tests.test("logistics_hatch_requester_asks_for_a_stack_more_and_an_empty_hand_clears_it", 20, HatchTests::requests);
        tests.test("logistics_hatch_provider_gives_an_empty_hand_its_first_stack", 20, HatchTests::gives);
        tests.test("logistics_hatch_saves_its_mode_request_and_contents", 20, HatchTests::saves);
        tests.test("logistics_hatch_a_hopper_fills_and_empties_it", 100, HatchTests::hoppers);
        tests.test("logistics_hatch_leaves_and_rejoins_its_station_as_the_platform_goes_and_comes", 20, HatchTests::rejoins);
        tests.test("logistics_hatch_placed_by_a_command_registers_as_it_loads", 20, HatchTests::command);
        tests.test("logistics_hatch_set_over_a_stale_record_of_the_other_mode_drops_it", 20, HatchTests::staleRecord);
        tests.test("logistics_hatch_breaking_it_drops_what_it_holds", 20, HatchTests::breaking);
        tests.test("logistics_hatch_one_a_command_removed_is_forgotten", 20, HatchTests::removedByCommand);
        tests.test("logistics_hatch_jade_shows_its_mode_request_and_station", 20, HatchTests::report);
    }

    // ---- crafting ----

    private static void crafts(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        var input = CraftingInput.of(3, 3, List.of(iron, iron, iron, iron, new ItemStack(Items.CHEST), iron,
                iron, new ItemStack(Items.HOPPER), iron));
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        if (recipe.isEmpty() || !recipe.get().value().assemble(input).is(RailworksRegistries.HATCH_ITEM.get())) {
            helper.fail("iron round a chest and a hopper did not craft a provider hatch", STATION);
        }
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("railworks", "hatch"));
        if (recipe.isPresent() && !recipe.get().id().equals(key)) {
            helper.fail("iron round a chest and a hopper crafted " + recipe.get().id() + ", not the hatch's recipe", STATION);
        }
        helper.succeed();
    }

    // ---- placing ----

    private static void places(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        hatch(helper, player, ACROSS, HatchMode.PROVIDER);
        hatch(helper, player, SECOND, HatchMode.REQUESTER);
        expectJoined(helper, FIRST, 0);
        expectJoined(helper, ACROSS, 0);
        expectJoined(helper, SECOND, 1);
        if (helper.getBlockState(SECOND).getValue(HatchBlock.MODE) != HatchMode.REQUESTER
                || record(helper, SECOND).mode() != HatchMode.REQUESTER) {
            helper.fail("a requester hatch did not place as a requester", SECOND);
        }
        if (station(helper).hatches() != 3) {
            helper.fail("the Station counts " + station(helper).hatches() + " hatches, not 3", STATION);
        }
        helper.succeed();
    }

    private static void refused(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.HATCH_ITEM.get(), 4));
        PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, AHEAD));
        if (plan == null || !plan.isRefused() || plan.refusal() != RailworksRefusal.HATCH_OFF_PLATFORM) {
            helper.fail("a hatch ahead of the Station planned " + plan + ", not refused off the platform", AHEAD);
        }
        if (place(helper, player, AHEAD).consumesAction() || !helper.getBlockState(AHEAD).isAir()
                || player.getMainHandItem().getCount() != 4) {
            helper.fail("a hatch ahead of the Station was placed or spent", AHEAD);
        }
        if (place(helper, player, LEADER).consumesAction() || !helper.getBlockState(LEADER).isAir()) {
            helper.fail("a hatch beside the leading car's place was placed", LEADER);
        }
        helper.succeed();
    }

    private static void freeSpots(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        BlockPos first = helper.absolutePos(FIRST);
        // Drawn spot by spot: two spots, each a box on either side of the Rail, a car gap short of its ends.
        List<AABB> spots = ownSpots(helper);
        if (spots.size() != 4 || !spots.stream().allMatch(box -> Math.abs(box.getXsize() - 1.8) < 1e-6 && box.getZsize() == 1.0)
                || spots.stream().noneMatch(box -> box.intersects(new AABB(first).deflate(0.01)))
                || spots.stream().anyMatch(box -> box.intersects(new AABB(helper.absolutePos(LEADER)).deflate(0.01))
                        || box.intersects(new AABB(helper.absolutePos(AHEAD)).deflate(0.01)))) {
            helper.fail("the free car spots are drawn as " + spots, STATION);
        }
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        List<AABB> left = ownSpots(helper);
        if (left.size() != 4 || left.stream().filter(box -> Math.abs(box.getXsize() - 0.9) < 1e-6).count() != 1
                || left.stream().anyMatch(box -> box.intersects(new AABB(first).deflate(0.01)))) {
            helper.fail("with a hatch beside the first spot, the free car spots are drawn as " + left, FIRST);
        }
        helper.succeed();
    }

    /** The free car spots drawn in this test's own plot: a server asks every Station of the level. */
    private static List<AABB> ownSpots(GameTestHelper helper) {
        Vec3 station = Vec3.atCenterOf(helper.absolutePos(STATION));
        return PlatformsNear.freeSpots(helper.getLevel(), helper.absolutePos(STATION)).stream()
                .filter(box -> box.getCenter().distanceTo(station) < 7.0)
                .toList();
    }

    // ---- using ----

    private static void switches(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.HATCH_ITEM.get(), 3));
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (!player.getMainHandItem().is(RailworksRegistries.REQUESTER_HATCH_ITEM.get()) || player.getMainHandItem().getCount() != 3) {
            helper.fail("sneak-used in the air, a stack of provider hatches became " + player.getMainHandItem(), STATION);
        }
        player.setShiftKeyDown(false);
        place(helper, player, FIRST);
        insert(helper, FIRST, new ItemStack(Items.COAL, 5));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        use(helper, player, FIRST);
        if (helper.getBlockState(FIRST).getValue(HatchBlock.MODE) != HatchMode.PROVIDER || record(helper, FIRST).mode() != HatchMode.PROVIDER) {
            helper.fail("an empty-hand sneak-use did not make the requester a provider", FIRST);
        }
        if (count(record(helper, FIRST).contents()) != 5) {
            helper.fail("switching it lost what it held", FIRST);
        }
        if (!helper.getLevel().getBlockState(helper.absolutePos(FIRST)).is(RailworksRegistries.HATCH.get())
                || !(helper.getBlockEntity(FIRST, HatchBlockEntity.class) instanceof HatchBlockEntity)) {
            helper.fail("switching it replaced the block", FIRST);
        }
        helper.succeed();
    }

    private static void requests(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.REQUESTER);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 7));
        use(helper, player, FIRST);
        use(helper, player, FIRST);
        expectRequest(helper, Optional.of(new HatchRequest<>(Items.IRON_INGOT, 2)));
        if (player.getMainHandItem().getCount() != 7) {
            helper.fail("asking took the item from the hand", FIRST);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL));
        use(helper, player, FIRST);
        expectRequest(helper, Optional.of(new HatchRequest<>(Items.COAL, 1)));
        for (int i = 0; i < Hatches.slots() + 3; i++) {
            use(helper, player, FIRST);
        }
        expectRequest(helper, Optional.of(new HatchRequest<>(Items.COAL, Hatches.slots())));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, FIRST);
        expectRequest(helper, Optional.empty());
        helper.succeed();
    }

    private static void gives(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        insert(helper, FIRST, new ItemStack(Items.IRON_INGOT, 10));
        insert(helper, FIRST, new ItemStack(Items.COAL, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, FIRST);
        if (!player.getMainHandItem().is(Items.IRON_INGOT) || player.getMainHandItem().getCount() != 10) {
            helper.fail("an empty hand took " + player.getMainHandItem() + ", not the first stack", FIRST);
        }
        if (count(record(helper, FIRST).contents()) != 3) {
            helper.fail("the hatch still holds " + count(record(helper, FIRST).contents()) + ", not the coal alone", FIRST);
        }
        helper.succeed();
    }

    // ---- what it holds ----

    private static void saves(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.REQUESTER);
        record(helper, FIRST).ask(Items.IRON_INGOT);
        insert(helper, FIRST, new ItemStack(Items.COAL, 9));
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Hatches hatches = LevelHatches.of(helper.getLevel());
        var saved = Hatches.CODEC.encodeStart(ops, hatches).getOrThrow();
        Hatches loaded = Hatches.CODEC.parse(ops, saved).getOrThrow();
        var hatch = loaded.at(helper.absolutePos(FIRST)).orElse(null);
        if (hatch == null) {
            helper.fail("the hatch was not saved", FIRST);
            return;
        }
        if (hatch.mode() != HatchMode.REQUESTER || !hatch.request().equals(Optional.of(new HatchRequest<>(Items.IRON_INGOT, 1)))
                || count(hatch.contents()) != 9 || !hatch.contents().getResource(0).is(Items.COAL)
                || hatch.contents().size() != Hatches.slots()) {
            helper.fail("the hatch loaded as " + hatch.mode() + " asking for " + hatch.request() + " holding "
                    + count(hatch.contents()) + " in " + hatch.contents().size() + " slots", FIRST);
        }
        helper.succeed();
    }

    /** A hopper over the hatch fills it, and one under it empties it, through the block. */
    private static void hoppers(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        BlockPos over = helper.absolutePos(FIRST.above());
        BlockPos under = helper.absolutePos(FIRST.below());
        helper.getLevel().setBlockAndUpdate(over, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) helper.getLevel().getBlockEntity(over)).setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(FIRST), Direction.UP);
        if (handler == null || handler.size() != Hatches.slots()) {
            helper.fail("the hatch gives no item handler of its slots: " + handler, FIRST);
            return;
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (count(record(helper, FIRST).contents()) != 2) {
                        helper.fail("the hopper over the hatch has not filled it yet", FIRST);
                    }
                })
                .thenExecute(() -> helper.getLevel().setBlockAndUpdate(under, Blocks.HOPPER.defaultBlockState()))
                .thenWaitUntil(() -> {
                    var hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(under);
                    int pulled = 0;
                    for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                        pulled += hopper.getItem(slot).getCount();
                    }
                    if (pulled != 2 || count(record(helper, FIRST).contents()) != 0) {
                        helper.fail("the hopper under the hatch has pulled " + pulled + " so far", FIRST);
                    }
                })
                .thenSucceed();
    }

    // ---- joining ----

    private static void rejoins(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        insert(helper, FIRST, new ItemStack(Items.COAL, 4));
        helper.setBlock(STATION, Blocks.AIR);
        expectJoined(helper, FIRST, -1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get()));
        StationTests.place(helper, player, STATION);
        expectJoined(helper, FIRST, 0);
        // Cutting the Span takes the platform away, and the hatch is a plain container still holding its coal.
        var network = LevelRails.of(helper.getLevel()).network();
        var span = LocomotiveTests.span(helper);
        network.remove(span.a(), span.b());
        expectJoined(helper, FIRST, -1);
        if (count(record(helper, FIRST).contents()) != 4) {
            helper.fail("the hatch lost what it held with its Station", FIRST);
        }
        network.add(span);
        expectJoined(helper, FIRST, 0);
        helper.succeed();
    }

    private static void command(GameTestHelper helper) {
        withStation(helper);
        helper.setBlock(FIRST, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.REQUESTER));
        BlockPos at = helper.absolutePos(FIRST);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    var hatch = LevelHatches.of(helper.getLevel()).at(at);
                    if (hatch.isEmpty() || hatch.get().mode() != HatchMode.REQUESTER) {
                        helper.fail("a hatch set by a command is in the level's hatches as " + hatch.map(Hatches.Hatch::mode), FIRST);
                    }
                    expectJoined(helper, FIRST, 0);
                })
                .thenSucceed();
    }

    /** A command removes a provider with no side effects, leaving its record, and sets a requester there. */
    private static void staleRecord(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        insert(helper, FIRST, new ItemStack(Items.COAL, 3));
        BlockPos at = helper.absolutePos(FIRST);
        helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        helper.setBlock(FIRST, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.REQUESTER));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    var hatch = record(helper, FIRST);
                    if (hatch.mode() != HatchMode.REQUESTER || count(hatch.contents()) != 0) {
                        helper.fail("the requester took over the stale provider's record: " + hatch.mode() + " holding "
                                + count(hatch.contents()), FIRST);
                    }
                    int coal = dropped(helper, at, Items.COAL);
                    if (coal != 3) {
                        helper.fail("the stale record's coal was not dropped: " + coal, FIRST);
                    }
                })
                .thenSucceed();
    }

    private static void breaking(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.REQUESTER);
        insert(helper, FIRST, new ItemStack(Items.COAL, 6));
        BlockPos at = helper.absolutePos(FIRST);
        helper.getLevel().destroyBlock(at, true);
        if (LevelHatches.of(helper.getLevel()).at(at).isPresent()) {
            helper.fail("a broken hatch is still in the level's hatches", FIRST);
        }
        int coal = dropped(helper, at, Items.COAL);
        boolean itself = dropped(helper, at, RailworksRegistries.REQUESTER_HATCH_ITEM.get()) > 0;
        if (coal != 6 || !itself) {
            helper.fail("a broken requester dropped " + coal + " coal" + (itself ? "" : " and not itself as a requester"), FIRST);
        }
        if (station(helper).hatches() != 0) {
            helper.fail("the Station still counts a broken hatch", STATION);
        }
        helper.succeed();
    }

    /** A command replacing a hatch skips its side effects: the Station's count finds the record stale, takes it out and drops what it held. */
    private static void removedByCommand(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        hatch(helper, player, SECOND, HatchMode.PROVIDER);
        insert(helper, FIRST, new ItemStack(Items.COAL, 5));
        BlockPos at = helper.absolutePos(FIRST);
        helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        if (LevelHatches.of(helper.getLevel()).at(at).isEmpty() || dropped(helper, at, Items.COAL) != 0) {
            helper.fail("the command ran the hatch's side effects: its record is gone or its coal dropped", FIRST);
        }
        if (station(helper).hatches() != 1 || LevelHatches.of(helper.getLevel()).at(at).isPresent()) {
            helper.fail("the Station counts " + station(helper).hatches() + " hatches after a command removed one", FIRST);
        }
        int coal = dropped(helper, at, Items.COAL);
        if (coal != 5) {
            helper.fail("the stale record's coal was not dropped at its block: " + coal, FIRST);
        }
        helper.succeed();
    }

    // ---- Jade ----

    private static void report(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        station(helper).rename(player, "Iron Mine");
        hatch(helper, player, SECOND, HatchMode.REQUESTER);
        record(helper, SECOND).ask(Items.IRON_INGOT);
        HatchReport report = HatchReport.of(helper.getBlockEntity(SECOND, HatchBlockEntity.class));
        expectLines(helper, report.lines(), List.of("tooltip.railworks.hatch.jade.requester",
                "message.railworks.hatch.requesting", "tooltip.railworks.hatch.jade.station"));
        Object[] args = ((TranslatableContents) report.lines().get(2).getContents()).getArgs();
        if (!"Iron Mine".equals(args[0]) || !Integer.valueOf(2).equals(args[1])) {
            helper.fail("Jade says the hatch joins " + args[0] + " at car spot " + args[1] + ", not the Iron Mine at 2", SECOND);
        }
        helper.setBlock(STATION, Blocks.AIR);
        expectLines(helper, HatchReport.of(helper.getBlockEntity(SECOND, HatchBlockEntity.class)).lines(),
                List.of("tooltip.railworks.hatch.jade.requester", "message.railworks.hatch.requesting",
                        "tooltip.railworks.hatch.jade.no_station"));
        helper.succeed();
    }

    // ---- setup ----

    /** {@link LocomotiveTests}' Span with a Station north of it, and a survival player beside it. */
    private static ServerPlayer withStation(GameTestHelper helper) {
        ServerPlayer player = LocomotiveTests.player(helper, -45.0F);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get()));
        if (!StationTests.place(helper, player, STATION).consumesAction()) {
            throw helper.assertionException(STATION, Component.literal("the Station was refused"));
        }
        return player;
    }

    /** A hatch of {@code mode} placed by {@code player} at {@code pos}, which must go. */
    private static void hatch(GameTestHelper helper, ServerPlayer player, BlockPos pos, HatchMode mode) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HatchItem.of(mode)));
        if (!place(helper, player, pos).consumesAction() || !helper.getBlockState(pos).is(RailworksRegistries.HATCH.get())) {
            throw helper.assertionException(pos, Component.literal("a hatch beside a car spot was refused"));
        }
    }

    /** A click placing the held item at {@code pos}, standing in the air: the click lands in the block itself. */
    private static BlockPlaceContext context(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static InteractionResult place(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        return player.getMainHandItem().useOn(context(helper, player, pos));
    }

    private static StationBlockEntity station(GameTestHelper helper) {
        return helper.getBlockEntity(STATION, StationBlockEntity.class);
    }

    private static Hatches.Hatch record(GameTestHelper helper, BlockPos pos) {
        return LevelHatches.of(helper.getLevel()).at(helper.absolutePos(pos))
                .orElseThrow(() -> helper.assertionException(pos, Component.literal("no hatch in the level's hatches")));
    }

    /** Uses {@code player}'s main hand on the top of the block at {@code pos}, as a click does. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.upFromBottomCenterOf(at, 1.0), Direction.UP, at, false));
    }

    private static void insert(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        ResourceHandler<ItemResource> contents = record(helper, pos).contents();
        try (var transaction = Transaction.openRoot()) {
            ResourceHandlerUtil.insertStacking(contents, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
    }

    /** How many of {@code item} lie dropped about the block at {@code at}, an absolute position. */
    private static int dropped(GameTestHelper helper, BlockPos at, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5)).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static int count(ResourceHandler<ItemResource> handler) {
        int count = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            count += handler.getAmountAsInt(slot);
        }
        return count;
    }

    /** The hatch at {@code pos} joins the Station at car spot {@code spot}, or none for -1. */
    private static void expectJoined(GameTestHelper helper, BlockPos pos, int spot) {
        Optional<Platform.Joined> joined = PlatformsNear.joined(helper.getLevel(), helper.absolutePos(pos));
        BlockPos station = helper.absolutePos(STATION);
        boolean right = spot < 0 ? joined.isEmpty()
                : joined.isPresent() && joined.get().spot() == spot && joined.get().station().x() == station.getX()
                && joined.get().station().y() == station.getY() && joined.get().station().z() == station.getZ();
        if (!right) {
            helper.fail("the hatch joins " + joined + ", not " + (spot < 0 ? "nothing" : "the Station at car spot " + spot), pos);
        }
    }

    private static void expectRequest(GameTestHelper helper, Optional<HatchRequest<Item>> expected) {
        if (!record(helper, FIRST).request().equals(expected)) {
            helper.fail("the requester asks for " + record(helper, FIRST).request() + ", not " + expected, FIRST);
        }
    }

    private static void expectLines(GameTestHelper helper, List<Component> lines, List<String> keys) {
        List<String> said = lines.stream()
                .map(line -> line.getContents() instanceof TranslatableContents contents ? contents.getKey() : line.getString())
                .toList();
        if (!said.equals(keys)) {
            helper.fail("Jade on the hatch says " + said + ", not " + keys, SECOND);
        }
    }
}
