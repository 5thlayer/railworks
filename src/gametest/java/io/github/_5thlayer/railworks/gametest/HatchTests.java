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
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.Ware;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.HatchRequest;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Hatches join a Station's platform (#36, ADR 0008): a Span runs south along x 5 from z -3, out
 * past the structure's north edge, to z 12, past its south edge, the plot's length, and a Station
 * east of its north end serves Trains heading north, so its platform runs back south from it: the
 * leading car's place beside z -3 to 1, its first five-block car spot beside z 2 to 6 and its second
 * beside z 7 to 11 (#70). Station and hatches stand in the row (#72): two blocks either side of the
 * Rail's line, x 3 and x 7, at the height of a Cargo car's chests, y 3, the Pylons' Arms being at
 * y 1. A hatch places in the row beside any block of a car spot or the leading car's place (#53)
 * and nowhere else, keeps its mode, request and
 * contents in the level's {@link LevelHatches}, is reached by hoppers through its block, and joins
 * the Station as long as its platform covers it. Which block is beside which spot is
 * {@code PlatformTest}'s. All share the {@code logistics_hatch_} prefix, and those for the row's
 * place, {@code logistics_hatch_row_}.
 */
final class HatchTests {

    /** The Rail's ends, a Pylon each: the Span runs south from the first, at the line's end. */
    private static final BlockPos NORTH_END = new BlockPos(5, 1, -3);
    private static final BlockPos SOUTH_END = new BlockPos(5, 1, 12);
    /** Where the Station stands, in the row east of the Rail's north end. */
    private static final BlockPos STATION = new BlockPos(7, 3, -3);
    /** Beside the leading car's place, which has no car spot but takes a hatch as one does: its third block. */
    private static final BlockPos LEADER = new BlockPos(7, 3, -1);
    /** Beside the first car spot in the row: its first block, nearest the Station. */
    private static final BlockPos FIRST = new BlockPos(7, 3, 2);
    /** Beside the first car spot across the Rail: its middle block. */
    private static final BlockPos ACROSS = new BlockPos(3, 3, 4);
    /** Beside the second car spot: its last block, the platform's last. */
    private static final BlockPos SECOND = new BlockPos(7, 3, 11);
    /** Ahead of the Station, beside no car spot. */
    private static final BlockPos AHEAD = new BlockPos(7, 3, -4);
    /** Beside the first spot's first block at the chests' height, but a block off the Rail, not two. */
    private static final BlockPos ONE_OFF = FIRST.west();
    /** Beside the first spot's first block where hatches stood before the row (#72): a block off the Rail at a car's floor. */
    private static final BlockPos OLD_ROW = FIRST.west().below();
    /** What {@link #expectJoined} expects of a hatch that joins no Station. */
    private static final int JOINS_NOTHING = Integer.MIN_VALUE;

    private HatchTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("logistics_hatch_crafts_from_iron_round_a_chest_and_a_hopper", 20, HatchTests::crafts);
        tests.test("logistics_hatch_places_beside_a_car_spot_or_the_leading_car_and_joins_its_station", 20, HatchTests::places);
        tests.test("logistics_hatch_is_refused_beside_no_car_spot", 20, HatchTests::refused);
        tests.test("logistics_hatch_row_places_two_off_the_rail_at_the_chests_height_and_nowhere_nearer_or_lower", 20, HatchTests::rowOnly);
        tests.test("logistics_hatch_row_a_held_hatch_outlines_the_rows_free_blocks_alone", 20, HatchTests::rowOutline);
        tests.test("logistics_hatch_free_car_spots_are_drawn_while_one_is_held", 20, HatchTests::freeSpots);
        tests.test("logistics_hatch_switches_in_the_air_and_placed_keeping_what_it_holds", 20, HatchTests::switches);
        tests.test("logistics_hatch_requester_asks_for_a_stack_more_and_an_empty_hand_clears_it", 20, HatchTests::requests);
        tests.test("logistics_hatch_provider_gives_an_empty_hand_its_first_stack", 20, HatchTests::gives);
        tests.test("logistics_hatch_saves_its_mode_request_and_contents", 20, HatchTests::saves);
        tests.test("logistics_hatch_a_hopper_fills_and_empties_it_and_what_it_shows_follows", 100, HatchTests::hoppers);
        tests.test("logistics_hatch_leaves_and_rejoins_its_station_as_the_platform_goes_and_comes", 20, HatchTests::rejoins);
        tests.test("logistics_hatch_placed_by_a_command_registers_as_it_loads", 20, HatchTests::command);
        tests.test("logistics_hatch_set_over_a_stale_record_of_the_other_mode_drops_it", 20, HatchTests::staleRecord);
        tests.test("logistics_hatch_breaking_it_drops_what_it_holds", 20, HatchTests::breaking);
        tests.test("logistics_hatch_one_a_command_removed_is_forgotten", 20, HatchTests::removedByCommand);
        tests.test("logistics_hatch_jade_shows_its_mode_request_and_station", 20, HatchTests::report);
        tests.test("logistics_hatch_shows_a_requesters_request_and_nothing_once_cleared", 40, HatchTests::showsRequest);
        tests.test("logistics_hatch_shows_a_providers_first_stack_as_it_fills_and_empties", 40, HatchTests::showsFirstStack);
        tests.test("logistics_hatch_shows_what_it_is_once_switched", 40, HatchTests::showsSwitched);
        tests.test("logistics_hatch_shows_what_a_command_sets_it_up_for", 40, HatchTests::showsCommand);
        tests.test("logistics_hatch_shows_its_record_once_its_block_loads_again", 40, HatchTests::showsReloaded);
        tests.test("logistics_hatch_shows_its_watchers_the_item_alone_and_keeps_it_while_unchanged", 40, HatchTests::showsUpdates);
        tests.test("logistics_hatch_shows_its_item_joining_no_station", 40, HatchTests::showsUnjoined);
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
        hatch(helper, player, LEADER, HatchMode.PROVIDER);
        expectJoined(helper, FIRST, 0);
        expectJoined(helper, ACROSS, 0);
        expectJoined(helper, SECOND, 1);
        expectJoined(helper, LEADER, Platform.LEADING);
        if (helper.getBlockState(SECOND).getValue(HatchBlock.MODE) != HatchMode.REQUESTER
                || record(helper, SECOND).mode() != HatchMode.REQUESTER) {
            helper.fail("a requester hatch did not place as a requester", SECOND);
        }
        if (station(helper).hatches() != 4) {
            helper.fail("the Station counts " + station(helper).hatches() + " hatches, not 4", STATION);
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
        helper.succeed();
    }

    private static void freeSpots(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        BlockPos first = helper.absolutePos(FIRST);
        AABB station = new AABB(helper.absolutePos(STATION)).deflate(0.01);
        // Drawn spot by spot: the leading car's five-block place and two five-block spots, each a box on
        // either side of the Rail, a car gap short of its ends. The Station stands in the row too, in the
        // first block of the leading car's place on its side, so that side's other four are drawn one by one.
        List<PlatformsNear.FreeSpot> free = ownFreeSpots(helper);
        List<AABB> spots = ownSpots(helper);
        List<PlatformsNear.FreeSpot> whole = free.stream().filter(spot -> spot.box().getZsize() > 1.0).toList();
        if (spots.size() != 9 || whole.size() != 5 || !whole.stream().allMatch(spot -> spot.box().getXsize() == 1.0
                        && Math.abs(spot.box().getZsize() - ((spot.leading() ? Platform.LEAD : Platform.SPOT) - CarBody.GAP)) < 1e-6)
                || spots.stream().noneMatch(box -> box.intersects(new AABB(first).deflate(0.01)))
                || spots.stream().noneMatch(box -> box.intersects(new AABB(helper.absolutePos(LEADER)).deflate(0.01)))
                || spots.stream().anyMatch(box -> box.intersects(station))
                || spots.stream().anyMatch(box -> box.intersects(new AABB(helper.absolutePos(AHEAD)).deflate(0.01)))) {
            helper.fail("the free car spots are drawn as " + spots, STATION);
        }
        // Those beside the leading car's place, either side of the Rail, are told apart: the whole side
        // across the Rail and the four the Station leaves on its own.
        List<AABB> leading = free.stream()
                .filter(PlatformsNear.FreeSpot::leading).map(PlatformsNear.FreeSpot::box).toList();
        AABB leader = new AABB(helper.absolutePos(LEADER)).deflate(0.01);
        AABB across = new AABB(helper.absolutePos(LEADER).west(4)).deflate(0.01);
        if (leading.size() != 5 || leading.stream().noneMatch(box -> box.intersects(leader))
                || leading.stream().noneMatch(box -> box.intersects(across))) {
            helper.fail("the free blocks beside the leading car's place are drawn as " + leading, LEADER);
        }
        // A hatch at the first spot's first block leaves its other four on that side drawn one by one,
        // the last half a car gap short of the spot's end.
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        List<AABB> left = ownSpots(helper);
        List<AABB> firstSpot = left.stream().filter(box -> box.getCenter().x > first.getX() && box.getCenter().x < first.getX() + 1
                && box.getCenter().z > first.getZ() && box.getCenter().z < first.getZ() + Platform.SPOT).toList();
        if (left.size() != 12 || firstSpot.size() != 4
                || firstSpot.stream().filter(box -> Math.abs(box.getZsize() - 0.9) < 1e-6).count() != 1
                || left.stream().anyMatch(box -> box.intersects(new AABB(first).deflate(0.01)))) {
            helper.fail("with a hatch beside the first spot, the free car spots are drawn as " + left, FIRST);
        }
        helper.succeed();
    }

    /**
     * Only the row takes a hatch (#72): beside the first spot's first block, one off the Rail at the
     * chests' height and one off at a car's floor, the old row, are refused with the Placement Preview
     * red, and the row's block, two off at the chests' height, places.
     */
    private static void rowOnly(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        for (BlockPos off : List.of(ONE_OFF, OLD_ROW)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.HATCH_ITEM.get(), 4));
            PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, off));
            if (plan == null || !plan.isRefused() || plan.refusal() != RailworksRefusal.HATCH_OFF_PLATFORM) {
                helper.fail("a hatch out of the row at " + off + " planned " + plan + ", not refused off the platform", off);
            }
            if (place(helper, player, off).consumesAction() || !helper.getBlockState(off).isAir()
                    || player.getMainHandItem().getCount() != 4) {
                helper.fail("a hatch out of the row at " + off + " was placed or spent", off);
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.HATCH_ITEM.get(), 4));
        PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, FIRST));
        if (plan == null || plan.isRefused()) {
            helper.fail("a hatch in the row planned " + plan + ", not a placing", FIRST);
        }
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        expectJoined(helper, FIRST, 0);
        helper.succeed();
    }

    /**
     * A held hatch outlines the row's free blocks (#72): every box drawn is two off the Rail at the
     * chests' height, the row's blocks beside the first spot among them, and the old row's and the
     * block between the row and the Rail not.
     */
    private static void rowOutline(GameTestHelper helper) {
        withStation(helper);
        BlockPos north = helper.absolutePos(NORTH_END);
        BlockPos row = helper.absolutePos(FIRST);
        List<AABB> spots = ownSpots(helper);
        if (spots.isEmpty() || spots.stream().anyMatch(box -> Math.abs(box.minY - row.getY()) > 1e-6
                || Math.abs(box.maxY - (row.getY() + 1)) > 1e-6
                || Math.abs(Math.abs(box.getCenter().x - (north.getX() + 0.5)) - 2.0) > 1e-6)) {
            helper.fail("the free blocks are not drawn in the row, two off the Rail at the chests' height: " + spots, FIRST);
        }
        for (BlockPos in : List.of(FIRST, ACROSS)) {
            AABB block = new AABB(helper.absolutePos(in)).deflate(0.01);
            if (spots.stream().noneMatch(box -> box.intersects(block))) {
                helper.fail("the row's block at " + in + " is not drawn among the free blocks " + spots, in);
            }
        }
        for (BlockPos out : List.of(OLD_ROW, ONE_OFF)) {
            AABB block = new AABB(helper.absolutePos(out)).deflate(0.01);
            if (spots.stream().anyMatch(box -> box.intersects(block))) {
                helper.fail("the block at " + out + ", out of the row, is drawn among the free blocks " + spots, out);
            }
        }
        helper.succeed();
    }

    /** The free car spots' boxes drawn in this test's own plot. */
    private static List<AABB> ownSpots(GameTestHelper helper) {
        return ownFreeSpots(helper).stream().map(PlatformsNear.FreeSpot::box).toList();
    }

    /**
     * The free car spots drawn in this test's own plot, beside its Rail between its ends: a server asks
     * every Station of the level.
     */
    private static List<PlatformsNear.FreeSpot> ownFreeSpots(GameTestHelper helper) {
        Vec3 north = Vec3.atCenterOf(helper.absolutePos(NORTH_END));
        Vec3 south = Vec3.atCenterOf(helper.absolutePos(SOUTH_END));
        return PlatformsNear.freeSpots(helper.getLevel(), helper.absolutePos(STATION)).stream()
                .filter(spot -> Math.abs(spot.box().getCenter().x - north.x) < 2.5
                        && spot.box().getCenter().z > north.z - 1.0 && spot.box().getCenter().z < south.z)
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
        expectRequest(helper, Optional.of(new HatchRequest<>(Ware.of(Items.IRON_INGOT), 2)));
        if (player.getMainHandItem().getCount() != 7) {
            helper.fail("asking took the item from the hand", FIRST);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL));
        use(helper, player, FIRST);
        expectRequest(helper, Optional.of(new HatchRequest<>(Ware.of(Items.COAL), 1)));
        for (int i = 0; i < Hatches.slots() + 3; i++) {
            use(helper, player, FIRST);
        }
        expectRequest(helper, Optional.of(new HatchRequest<>(Ware.of(Items.COAL), Hatches.slots())));
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
        if (hatch.mode() != HatchMode.REQUESTER || !hatch.request().equals(Optional.of(new HatchRequest<>(Ware.of(Items.IRON_INGOT), 1)))
                || count(hatch.contents()) != 9 || !hatch.contents().getResource(0).is(Items.COAL)
                || hatch.contents().size() != Hatches.slots()) {
            helper.fail("the hatch loaded as " + hatch.mode() + " asking for " + hatch.request() + " holding "
                    + count(hatch.contents()) + " in " + hatch.contents().size() + " slots", FIRST);
        }
        helper.succeed();
    }

    /** A hopper over the hatch fills it, and one under it empties it, through the block, what it shows following. */
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
                .thenWaitUntil(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
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
                .thenWaitUntil(() -> expectShown(helper, FIRST, ItemStack.EMPTY))
                .thenSucceed();
    }

    // ---- joining ----

    private static void rejoins(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        insert(helper, FIRST, new ItemStack(Items.COAL, 4));
        helper.setBlock(STATION, Blocks.AIR);
        expectJoined(helper, FIRST, JOINS_NOTHING);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get()));
        StationTests.place(helper, player, STATION);
        expectJoined(helper, FIRST, 0);
        // Cutting the Span takes the platform away, and the hatch is a plain container still holding its coal.
        var network = LevelRails.of(helper.getLevel()).network();
        var span = span(helper);
        network.remove(span.a(), span.b());
        expectJoined(helper, FIRST, JOINS_NOTHING);
        if (count(record(helper, FIRST).contents()) != 4) {
            helper.fail("the hatch lost what it held with its Station", FIRST);
        }
        network.add(span);
        expectJoined(helper, FIRST, 0);
        helper.succeed();
    }

    private static void command(GameTestHelper helper) {
        withStation(helper);
        setHatch(helper, FIRST, HatchMode.REQUESTER);
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
        setHatch(helper, FIRST, HatchMode.REQUESTER);
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
        hatch(helper, player, LEADER, HatchMode.PROVIDER);
        expectLines(helper, HatchReport.of(helper.getBlockEntity(LEADER, HatchBlockEntity.class)).lines(),
                List.of("tooltip.railworks.hatch.jade.provider", "tooltip.railworks.hatch.jade.station_leading"));
        helper.setBlock(STATION, Blocks.AIR);
        expectLines(helper, HatchReport.of(helper.getBlockEntity(SECOND, HatchBlockEntity.class)).lines(),
                List.of("tooltip.railworks.hatch.jade.requester", "message.railworks.hatch.requesting",
                        "tooltip.railworks.hatch.jade.no_station"));
        helper.succeed();
    }

    // ---- what it shows ----

    private static void showsRequest(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.REQUESTER);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, ItemStack.EMPTY))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 7));
                    use(helper, player, FIRST);
                    use(helper, player, FIRST);
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL));
                    use(helper, player, FIRST);
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.COAL)))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    use(helper, player, FIRST);
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, ItemStack.EMPTY))
                .thenSucceed();
    }

    /** Filled and emptied through its record, as a hopper or a Train does, and by a player's empty hand. */
    private static void showsFirstStack(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, ItemStack.EMPTY))
                .thenExecute(() -> {
                    insert(helper, FIRST, new ItemStack(Items.IRON_INGOT, 10));
                    insert(helper, FIRST, new ItemStack(Items.COAL, 3));
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    use(helper, player, FIRST);
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.COAL)))
                .thenExecute(() -> {
                    var contents = record(helper, FIRST).contents();
                    try (var transaction = Transaction.openRoot()) {
                        contents.extract(ItemResource.of(Items.COAL), 3, transaction);
                        transaction.commit();
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, ItemStack.EMPTY))
                // Plain iron first, named iron after it: once the plain is taken, the named shows, with its
                // name, though the item is the same, and a watcher reading the update tag sees the name too.
                .thenExecute(() -> {
                    insert(helper, FIRST, new ItemStack(Items.IRON_INGOT));
                    insert(helper, FIRST, named());
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
                .thenExecute(() -> {
                    try (var transaction = Transaction.openRoot()) {
                        record(helper, FIRST).contents().extract(ItemResource.of(Items.IRON_INGOT), 1, transaction);
                        transaction.commit();
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    expectShown(helper, FIRST, named());
                    var entity = helper.getBlockEntity(FIRST, HatchBlockEntity.class);
                    var registries = helper.getLevel().registryAccess();
                    var watcher = new HatchBlockEntity(entity.getBlockPos(), entity.getBlockState());
                    watcher.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, registries,
                            entity.getUpdateTag(registries)));
                    if (!ItemStack.matches(watcher.shown(), named())) {
                        helper.fail("a watcher reading the update tag shows " + watcher.shown() + ", not the named iron", FIRST);
                    }
                })
                .thenSucceed();
    }

    private static ItemStack named() {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Ore"));
        return stack;
    }

    /** A requester asking for iron and holding coal shows the coal once a provider, and nothing once a requester again, its request cleared. */
    private static void showsSwitched(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.REQUESTER);
        record(helper, FIRST).ask(Items.IRON_INGOT);
        insert(helper, FIRST, new ItemStack(Items.COAL, 4));
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
                .thenExecute(() -> sneakUse(helper, player, FIRST))
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.COAL)))
                .thenExecute(() -> sneakUse(helper, player, FIRST))
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, ItemStack.EMPTY))
                .thenSucceed();
    }

    private static void showsCommand(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        hatch(helper, player, SECOND, HatchMode.REQUESTER);
        CommandTests.run(helper, "railworks hatch %s provider minecraft:raw_iron 2", FIRST);
        CommandTests.run(helper, "railworks hatch %s requester minecraft:raw_gold 3", SECOND);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.RAW_IRON)))
                .thenExecute(() -> expectShown(helper, SECOND, new ItemStack(Items.RAW_GOLD)))
                .thenSucceed();
    }

    /**
     * A hatch's block set afresh over its record, as a chunk loading again makes its block entity anew,
     * shows what the record holds, though the record changed while no block entity watched it.
     */
    private static void showsReloaded(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        BlockPos at = helper.absolutePos(FIRST);
        helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        insert(helper, FIRST, new ItemStack(Items.IRON_INGOT, 5));
        setHatch(helper, FIRST, HatchMode.PROVIDER);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
                .thenSucceed();
    }

    /** Set by a command where no platform covers it, a provider shows its first stack all the same. */
    private static void showsUnjoined(GameTestHelper helper) {
        setHatch(helper, FIRST, HatchMode.PROVIDER);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    expectJoined(helper, FIRST, JOINS_NOTHING);
                    insert(helper, FIRST, new ItemStack(Items.IRON_INGOT, 5));
                })
                .thenIdle(1)
                .thenExecute(() -> expectShown(helper, FIRST, new ItemStack(Items.IRON_INGOT)))
                .thenSucceed();
    }

    /** Its update tag holds the item alone, and nothing for a hatch showing nothing; while it is unchanged, the stack shown is never replaced, so nothing is sent. */
    private static void showsUpdates(GameTestHelper helper) {
        ServerPlayer player = withStation(helper);
        hatch(helper, player, FIRST, HatchMode.PROVIDER);
        var registries = helper.getLevel().registryAccess();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    var tag = helper.getBlockEntity(FIRST, HatchBlockEntity.class).getUpdateTag(registries);
                    if (!tag.isEmpty()) {
                        helper.fail("a hatch showing nothing sends " + tag, FIRST);
                    }
                    insert(helper, FIRST, new ItemStack(Items.IRON_INGOT, 5));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    var entity = helper.getBlockEntity(FIRST, HatchBlockEntity.class);
                    var tag = entity.getUpdateTag(registries);
                    var watcher = new HatchBlockEntity(entity.getBlockPos(), entity.getBlockState());
                    watcher.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
                    if (tag.size() != 1 || !ItemStack.matches(watcher.shown(), new ItemStack(Items.IRON_INGOT))) {
                        helper.fail("a hatch's update tag holds " + tag.keySet() + ", and a watcher reading it shows "
                                + watcher.shown() + ", not the iron alone", FIRST);
                    }
                    ItemStack shown = entity.shown();
                    insert(helper, FIRST, new ItemStack(Items.IRON_INGOT, 5));
                    insert(helper, FIRST, new ItemStack(Items.COAL, 5));
                    helper.runAfterDelay(3, () -> {
                        if (entity.shown() != shown) {
                            helper.fail("the hatch replaced the item it shows, sending it again, though it did not change", FIRST);
                        }
                    });
                })
                .thenIdle(5)
                .thenSucceed();
    }

    // ---- setup ----

    /** The Span with a Station east of its north end, and a survival player beside it. */
    private static ServerPlayer withStation(GameTestHelper helper) {
        for (BlockPos end : List.of(NORTH_END, SOUTH_END)) {
            helper.setBlock(end, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        LevelRails.of(helper.getLevel()).network().add(span(helper));
        // The row is in the air over the structure's floor: something to click on under the Station.
        helper.setBlock(STATION.below(), Blocks.STONE);
        ServerPlayer player = LogisticsTests.near(helper, new BlockPos(7, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get()));
        if (!StationTests.place(helper, player, STATION).consumesAction()) {
            throw helper.assertionException(STATION, Component.literal("the Station was refused"));
        }
        return player;
    }

    /** The Span between the Rail's ends. */
    private static Span span(GameTestHelper helper) {
        var one = PylonColumn.arm(helper.absolutePos(NORTH_END));
        var other = PylonColumn.arm(helper.absolutePos(SOUTH_END));
        return Span.of(one, other, SpanRules.cost(one, other));
    }

    /** A hatch of {@code mode} placed by {@code player} at {@code pos}, which must go. */
    private static void hatch(GameTestHelper helper, ServerPlayer player, BlockPos pos, HatchMode mode) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HatchItem.of(Goods.Kind.ITEMS, mode)));
        if (!place(helper, player, pos).consumesAction() || !helper.getBlockState(pos).is(RailworksRegistries.HATCH.get())) {
            throw helper.assertionException(pos, Component.literal("a hatch beside a car spot was refused"));
        }
    }

    /** A hatch of {@code mode} set at {@code pos} as a command sets one, with no player, registering as it loads. */
    private static void setHatch(GameTestHelper helper, BlockPos pos, HatchMode mode) {
        helper.setBlock(pos, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, mode));
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
        boolean right = spot == JOINS_NOTHING ? joined.isEmpty()
                : joined.isPresent() && joined.get().spot() == spot && joined.get().station().x() == station.getX()
                && joined.get().station().y() == station.getY() && joined.get().station().z() == station.getZ();
        if (!right) {
            helper.fail("the hatch joins " + joined + ", not " + (spot == JOINS_NOTHING ? "nothing" : "the Station at car spot " + spot), pos);
        }
    }

    /** Sneak-uses the hatch at {@code pos} with an empty hand, switching it. */
    private static void sneakUse(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        use(helper, player, pos);
        player.setShiftKeyDown(false);
    }

    /** The hatch at {@code pos} shows {@code expected}, one of it, or nothing for an empty stack. */
    private static void expectShown(GameTestHelper helper, BlockPos pos, ItemStack expected) {
        ItemStack shown = helper.getBlockEntity(pos, HatchBlockEntity.class).shown();
        if (!ItemStack.matches(shown, expected)) {
            helper.fail("the hatch shows " + shown + ", not " + expected, pos);
        }
    }

    private static void expectRequest(GameTestHelper helper, Optional<HatchRequest<Ware>> expected) {
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
