// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.libworks.WorksTab;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Module's blocks, items, entity and block entity types, and the held Span end. */
public final class RailworksRegistries {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Railworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Railworks.MOD_ID);
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Railworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Railworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Railworks.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Railworks.MOD_ID);

    /** One segment of a Pylon column. */
    public static final DeferredBlock<PylonBlock> PYLON = BLOCKS.registerBlock(PylonBlock.BLOCK_NAME, PylonBlock::new);
    public static final DeferredItem<PylonItem> PYLON_ITEM =
            ITEMS.registerItem(PylonBlock.BLOCK_NAME, props -> new PylonItem(PYLON.get(), props));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PylonBlockEntity>> PYLON_ENTITY =
            BLOCK_ENTITIES.register(PylonBlock.BLOCK_NAME,
                    () -> new BlockEntityType<>(PylonBlockEntity::new, PYLON.get()));

    /** What strings a Span, one item per block of its length. */
    public static final DeferredItem<Item> RAIL = ITEMS.registerSimpleItem("rail");

    /** Iron and a furnace: the Train's first car, set on a Span. */
    public static final DeferredItem<LocomotiveItem> LOCOMOTIVE =
            ITEMS.registerItem("burner_locomotive", LocomotiveItem::new);
    public static final DeferredHolder<EntityType<?>, EntityType<LocomotiveEntity>> LOCOMOTIVE_ENTITY =
            ENTITIES.registerEntityType("burner_locomotive", LocomotiveEntity::new, MobCategory.MISC, builder -> builder
                    .sized(LocomotiveEntity.WIDTH, LocomotiveEntity.HEIGHT)
                    .passengerAttachments(1.2F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSummon());

    /** A named place by the Rail that a Train stops at. */
    public static final DeferredBlock<StationBlock> STATION = BLOCKS.registerBlock(StationBlock.BLOCK_NAME, StationBlock::new);
    public static final DeferredItem<StationItem> STATION_ITEM =
            ITEMS.registerItem(StationBlock.BLOCK_NAME, props -> new StationItem(STATION.get(), props));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> STATION_ENTITY =
            BLOCK_ENTITIES.register(StationBlock.BLOCK_NAME,
                    () -> new BlockEntityType<>(StationBlockEntity::new, STATION.get()));

    /** The Locomotive's menu. */
    public static final DeferredHolder<MenuType<?>, MenuType<LocomotiveMenu>> LOCOMOTIVE_MENU =
            MENUS.register("burner_locomotive", () -> new MenuType<>(LocomotiveMenu::new, FeatureFlags.VANILLA_SET));

    /** The first Arm of a Span that a Rail is holding, on the stack so it survives a relog and the client can see it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> PENDING_SPAN =
            DATA_COMPONENTS.register("pending_span", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    private RailworksRegistries() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
    }
}
