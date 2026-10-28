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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * The Module's blocks, items, entity and block entity types, the held Span end, the hatch's and the
 * Burner Generator's item handlers, and the energy handlers of the Charger, the Burner Generator and
 * the Electric Locomotive.
 */
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

    /** Iron and a furnace: the car that pulls a Train, set on a Span. */
    public static final DeferredItem<CarItem> LOCOMOTIVE =
            ITEMS.registerItem("burner_locomotive", props -> new CarItem(CarKind.LOCOMOTIVE, props));
    public static final DeferredHolder<EntityType<?>, EntityType<LocomotiveEntity>> LOCOMOTIVE_ENTITY =
            ENTITIES.registerEntityType("burner_locomotive", LocomotiveEntity::new, MobCategory.MISC, builder -> builder
                    .sized(CarEntity.WIDTH, CarEntity.HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSummon());

    /** A Burner Locomotive, copper and redstone: the Locomotive that runs on a charge of FE. */
    public static final DeferredItem<CarItem> ELECTRIC_LOCOMOTIVE =
            ITEMS.registerItem("electric_locomotive", props -> new CarItem(CarKind.ELECTRIC_LOCOMOTIVE, props));
    public static final DeferredHolder<EntityType<?>, EntityType<LocomotiveEntity>> ELECTRIC_LOCOMOTIVE_ENTITY =
            ENTITIES.registerEntityType("electric_locomotive", LocomotiveEntity::new, MobCategory.MISC, builder -> builder
                    .sized(CarEntity.WIDTH, CarEntity.HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSummon());

    /** Iron and a chest: a car that carries items, coupled into a Train. */
    public static final DeferredItem<CarItem> CARGO_CAR =
            ITEMS.registerItem("cargo_car", props -> new CarItem(CarKind.CARGO, props));
    public static final DeferredHolder<EntityType<?>, EntityType<CargoCarEntity>> CARGO_CAR_ENTITY =
            ENTITIES.registerEntityType("cargo_car", CargoCarEntity::new, MobCategory.MISC, builder -> builder
                    .sized(CarEntity.WIDTH, CarEntity.HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSummon());

    /** A named block beside or under the Rail, which a car stopped by it is at. */
    public static final DeferredBlock<StationBlock> STATION = BLOCKS.registerBlock(StationBlock.BLOCK_NAME, StationBlock::new);
    public static final DeferredItem<StationItem> STATION_ITEM =
            ITEMS.registerItem(StationBlock.BLOCK_NAME, props -> new StationItem(STATION.get(), props));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> STATION_ENTITY =
            BLOCK_ENTITIES.register(StationBlock.BLOCK_NAME,
                    () -> new BlockEntityType<>(StationBlockEntity::new, STATION.get()));

    /** A container beside a Station's platform, joining that Station: a provider or a requester. */
    public static final DeferredBlock<HatchBlock> HATCH = BLOCKS.registerBlock(HatchBlock.BLOCK_NAME, HatchBlock::new);
    /**
     * A provider hatch in hand, the block's own item, which its recipe makes. Each mode's item has its
     * own name, so Jade names a hatch by the item it is picked as.
     */
    public static final DeferredItem<HatchItem> HATCH_ITEM = ITEMS.registerItem(HatchBlock.BLOCK_NAME,
            props -> new HatchItem(HATCH.get(), HatchMode.PROVIDER, props), Item.Properties::useItemDescriptionPrefix);
    /** A requester hatch in hand, which a provider's turns into, sneak-used in the air, and back. */
    public static final DeferredItem<HatchItem> REQUESTER_HATCH_ITEM = ITEMS.registerItem("requester_hatch",
            props -> new HatchItem(HATCH.get(), HatchMode.REQUESTER, props), Item.Properties::useItemDescriptionPrefix);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HatchBlockEntity>> HATCH_ENTITY =
            BLOCK_ENTITIES.register(HatchBlock.BLOCK_NAME,
                    () -> new BlockEntityType<>(HatchBlockEntity::new, HATCH.get()));

    /** A block beside a Station's platform, joining that Station, that charges the Electric Locomotive beside it. */
    public static final DeferredBlock<ChargerBlock> CHARGER = BLOCKS.registerBlock(ChargerBlock.BLOCK_NAME,
            props -> new ChargerBlock(props, false));
    public static final DeferredItem<ChargerItem> CHARGER_ITEM = ITEMS.registerItem(ChargerBlock.BLOCK_NAME,
            props -> new ChargerItem(CHARGER.get(), props));
    /** A Charger that never runs out, for creative play and tests, with no recipe. */
    public static final DeferredBlock<ChargerBlock> CREATIVE_CHARGER = BLOCKS.registerBlock(ChargerBlock.CREATIVE_NAME,
            props -> new ChargerBlock(props, true));
    public static final DeferredItem<ChargerItem> CREATIVE_CHARGER_ITEM = ITEMS.registerItem(ChargerBlock.CREATIVE_NAME,
            props -> new ChargerItem(CREATIVE_CHARGER.get(), props));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargerBlockEntity>> CHARGER_ENTITY =
            BLOCK_ENTITIES.register(ChargerBlock.BLOCK_NAME,
                    () -> new BlockEntityType<>(ChargerBlockEntity::new, CHARGER.get(), CREATIVE_CHARGER.get()));

    /** A block that burns furnace fuel into FE for the blocks touching it. */
    public static final DeferredBlock<BurnerGeneratorBlock> BURNER_GENERATOR =
            BLOCKS.registerBlock(BurnerGeneratorBlock.BLOCK_NAME, BurnerGeneratorBlock::new);
    public static final DeferredItem<BlockItem> BURNER_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(BURNER_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerGeneratorBlockEntity>> BURNER_GENERATOR_ENTITY =
            BLOCK_ENTITIES.register(BurnerGeneratorBlock.BLOCK_NAME,
                    () -> new BlockEntityType<>(BurnerGeneratorBlockEntity::new, BURNER_GENERATOR.get()));

    /** The Locomotive's menu. */
    public static final DeferredHolder<MenuType<?>, MenuType<LocomotiveMenu>> LOCOMOTIVE_MENU =
            MENUS.register("burner_locomotive", () -> new MenuType<>(LocomotiveMenu::new, FeatureFlags.VANILLA_SET));
    /** The Electric Locomotive's menu, with a charge gauge in place of fuel slots. */
    public static final DeferredHolder<MenuType<?>, MenuType<LocomotiveMenu>> ELECTRIC_LOCOMOTIVE_MENU =
            MENUS.register("electric_locomotive", () -> new MenuType<>(LocomotiveMenu::electricClient, FeatureFlags.VANILLA_SET));
    /** The Burner Generator's menu. */
    public static final DeferredHolder<MenuType<?>, MenuType<BurnerGeneratorMenu>> BURNER_GENERATOR_MENU =
            MENUS.register(BurnerGeneratorBlock.BLOCK_NAME, () -> new MenuType<>(BurnerGeneratorMenu::new, FeatureFlags.VANILLA_SET));

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
        modBus.addListener(RailworksRegistries::registerCapabilities);
    }

    /**
     * A hatch's item handler, on every side: its record's slots. A Charger's energy handler, insert
     * only, and a Burner Generator's, extract only, beside its fuel slot; an Electric Locomotive's,
     * read only, onto its charge.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, HATCH_ENTITY.get(), (hatch, side) -> hatch.items());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CHARGER_ENTITY.get(), (charger, side) -> charger.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BURNER_GENERATOR_ENTITY.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, BURNER_GENERATOR_ENTITY.get(), WorldlyContainerWrapper::new);
        event.registerEntity(Capabilities.Energy.ENTITY, ELECTRIC_LOCOMOTIVE_ENTITY.get(), (car, side) -> car.charge());
    }
}
