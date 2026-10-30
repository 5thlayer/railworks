// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.FluidCarEntity;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.rail.CarBody;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * A car, drawn from its {@link CarBody}: a boxy car straddling the Rail, on a Cargo car a vanilla chest
 * set into each side, and on a Fluid car a tank. Each box is drawn in its {@link CarBody.Panel}'s
 * texture, Riley's steel plating, tiled at 16 px per block with its UVs taken from the car's own
 * coordinates, so a panel's pattern runs on from one box to the next without a seam; a face is cut at
 * every whole block, so no quad wraps past its sprite on the block atlas. The lamps and the Sight
 * glasses' panes stay flat colours.
 *
 * <p>A Fluid car's Sight glasses are filled, on their panes' outer faces, with its tank's fluid as
 * the server last synced it ({@link SightGlassFluid}), tiled a block to a tile from the pane's bottom,
 * so the surface only crops the top tile.
 */
public final class CarRenderer<T extends CarEntity> extends EntityRenderer<T, CarRenderer.State> {

    public static final class State extends EntityRenderState {
        float yRot;
        float xRot;
        /** A Fluid car's fluid; null for none, or another car. */
        @Nullable SightGlassFluid fluid;
        /** How full the tank is: its amount and capacity, in millibuckets. */
        int amount;
        int capacity;
    }

    /**
     * How a panel is drawn: in {@code sprite}, under {@code textures/block/car/}, tinted
     * {@code colour} (ARGB), and when {@code centred} with its texture's middle at the box's middle up
     * its sides rather than laid by the car's coordinates, so a band's stripe runs along the band.
     */
    private record Look(SpriteId sprite, int colour, boolean centred) {
    }

    private static Look look(CarBody.Panel panel) {
        return switch (panel) {
            case SHELL -> new Look(sprite("shell"), -1, false);
            case ROOF -> new Look(sprite("roof"), -1, false);
            case SKIRT -> new Look(sprite("skirt"), -1, false);
            case TRIM -> new Look(sprite("trim"), -1, false);
            case WINDOW -> new Look(sprite("window"), -1, true);
            case BURNER_NOSE -> new Look(sprite("burner_nose"), -1, true);
            case ELECTRIC_NOSE -> new Look(sprite("electric_nose"), -1, true);
            case TANK -> new Look(sprite("tank"), -1, false);
            // Flat colours, on a white sprite.
            case LAMP -> new Look(WHITE, 0xFFF2E59E, false);
            case GLASS -> new Look(WHITE, 0xFF0D0F12, false);
        };
    }

    private static final SpriteId WHITE = sprite("paint");
    /**
     * Where a panel's tiles are laid from, in the car's coordinates: its x and z on block edges,
     * since a car's middle block runs from -0.5 to 0.5, so a stopped car's tiles line up with the blocks.
     */
    private static final float TILE_X = 0.5F;
    private static final float TILE_Z = 0.5F;
    private final CarBody body;
    private final ChestModel chest;
    private final SpriteGetter sprites;

    private CarRenderer(EntityRendererProvider.Context context, CarBody body) {
        super(context);
        this.body = body;
        this.chest = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
        this.sprites = context.getSprites();
        this.shadowRadius = 0.0F;
    }

    public static <T extends CarEntity> CarRenderer<T> locomotive(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.LOCOMOTIVE);
    }

    public static <T extends CarEntity> CarRenderer<T> electric(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.ELECTRIC);
    }

    public static <T extends CarEntity> CarRenderer<T> cargo(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.CARGO);
    }

    public static <T extends CarEntity> CarRenderer<T> fluid(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.FLUID);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T car, State state, float partialTicks) {
        super.extractRenderState(car, state, partialTicks);
        state.yRot = Mth.rotLerp(partialTicks, car.yRotO, car.getYRot());
        state.xRot = Mth.lerp(partialTicks, car.xRotO, car.getXRot());
        state.fluid = null;
        if (car instanceof FluidCarEntity fluidCar) {
            FluidStack tank = fluidCar.tankSeen();
            state.fluid = SightGlassFluid.of(tank);
            state.amount = tank.getAmount();
            state.capacity = fluidCar.tankCapacitySeen();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // The body is built facing +z. The car's origin is the middle of its floor, on the flange's
        // top, and it pitches about that point so a climb keeps the floor on the Rail.
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            for (CarBody.Part part : body.parts()) {
                Look look = look(part.panel());
                TextureAtlasSprite sprite = sprites.get(look.sprite());
                if (look.sprite() == WHITE) {
                    flatBox(pose, buffer, part.box(), sprite, look.colour(), light);
                } else {
                    tiledBox(pose, buffer, part.box(), sprite, look.centred(), light);
                }
            }
        });
        for (CarBody.Box chest : body.chests()) {
            submitChest(chest, poseStack, collector, light);
        }
        SightGlassFluid fluid = state.fluid;
        if (fluid != null) {
            int fluidLight = fluid.light(light);
            collector.submitCustomGeometry(poseStack, fluid.renderType(), (pose, buffer) -> {
                for (CarBody.SightGlass glass : body.sightGlasses()) {
                    fill(pose, buffer, glass, (float) glass.surface(state.amount, state.capacity), fluid, fluidLight);
                }
            });
        }
        poseStack.popPose();
    }

    /**
     * The fluid in {@code glass} up to {@code surface}, on its pane's outer face: one quad per tile of
     * a block, from the pane's bottom and its back end, the last row cropped at the surface and the
     * last column at the pane's front end, each showing as much of the sprite as it covers.
     */
    private static void fill(PoseStack.Pose pose, VertexConsumer buffer, CarBody.SightGlass glass, float surface,
                             SightGlassFluid fluid, int light) {
        CarBody.Box pane = glass.pane();
        float side = (float) glass.side();
        float x = side > 0 ? (float) pane.maxX() + SightGlassFluid.PROUD : (float) pane.minX() - SightGlassFluid.PROUD;
        float bottom = (float) pane.minY();
        float back = (float) pane.minZ();
        for (float y0 = bottom; y0 < surface; y0 += 1.0F) {
            float y1 = Math.min(y0 + 1.0F, surface);
            for (float z0 = back; z0 < pane.maxZ(); z0 += 1.0F) {
                float z1 = Math.min(z0 + 1.0F, (float) pane.maxZ());
                // The sprite runs along z from the tile's back end on both sides, so a cropped tile
                // still meets the next at the sprite's edge.
                float uBack = 0.0F;
                float uFront = z1 - z0;
                // The sprite's top is v 0: a cropped row shows its lower part, as a block's fluid shows its top cut off.
                float v1 = 1.0F;
                float v0 = 1.0F - (y1 - y0);
                // Counter-clockwise as seen from the side it faces, from the bottom left, which is
                // the tile's front end on the +x side and its back end on the -x side.
                float left = side > 0 ? z1 : z0;
                float right = side > 0 ? z0 : z1;
                float uLeft = side > 0 ? uFront : uBack;
                float uRight = side > 0 ? uBack : uFront;
                fluid.vertex(pose, buffer, x, y0, left, uLeft, v1, light, side, 0.0F, 0.0F);
                fluid.vertex(pose, buffer, x, y0, right, uRight, v1, light, side, 0.0F, 0.0F);
                fluid.vertex(pose, buffer, x, y1, right, uRight, v0, light, side, 0.0F, 0.0F);
                fluid.vertex(pose, buffer, x, y1, left, uLeft, v0, light, side, 0.0F, 0.0F);
            }
        }
    }

    /** A closed chest filling the cube as it would a block, its front out of the car's nearer side. */
    private void submitChest(CarBody.Box cube, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        float size = (float) (cube.maxY() - cube.minY());
        boolean right = cube.minX() + cube.maxX() > 0.0;
        poseStack.pushPose();
        poseStack.translate((cube.minX() + cube.maxX()) / 2.0, cube.minY(), (cube.minZ() + cube.maxZ()) / 2.0);
        // The chest's model faces +z; turn it to face out of its side.
        poseStack.mulPose(Axis.YP.rotationDegrees(right ? 90.0F : -90.0F));
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        collector.submitModel(chest, 0.0F, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                Sheets.CHEST_REGULAR.select(ChestType.SINGLE), sprites, 0, null);
        poseStack.popPose();
    }

    private static SpriteId sprite(String name) {
        return Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "car/" + name));
    }

    /** A box in one flat colour, each face one quad on the middle of the white sprite. */
    private static void flatBox(PoseStack.Pose pose, VertexConsumer buffer, CarBody.Box b, TextureAtlasSprite white,
                                int colour, int light) {
        float u = white.getU(0.5F);
        float v = white.getV(0.5F);
        for (Face face : Face.values()) {
            float[][] corners = face.corners(b);
            for (float[] corner : corners) {
                buffer.addVertex(pose, corner[0], corner[1], corner[2]).setColor(colour).setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, face.normalX, face.normalY, face.normalZ);
            }
        }
    }

    /**
     * A box in {@code sprite}, each face tiled a block to a tile by its own coordinates: on a side, u
     * runs along it as seen from outside and v up it; on the top and the bottom, u runs along x and v
     * along -z. A face is cut at every whole block of u and v, each piece showing the part of the
     * sprite it covers. Tiles are laid from {@link #TILE_X} and {@link #TILE_Z}, and up a side from the
     * car's floor, or when {@code centred} from half a block under the box's middle.
     */
    private static void tiledBox(PoseStack.Pose pose, VertexConsumer buffer, CarBody.Box box, TextureAtlasSprite sprite,
                                 boolean centred, int light) {
        CarBody.Box b = new CarBody.Box(box.minX() + TILE_X, box.minY(), box.minZ() + TILE_Z,
                box.maxX() + TILE_X, box.maxY(), box.maxZ() + TILE_Z);
        float lift = centred ? 0.5F - (float) (b.minY() + b.maxY()) / 2.0F : 0.0F;
        for (Face face : Face.values()) {
            float[] range = face.uvRange(b, face.side ? lift : 0.0F);
            for (float u0 = range[0]; u0 < range[1]; u0 = (float) Math.floor(u0) + 1.0F) {
                float u1 = Math.min((float) Math.floor(u0) + 1.0F, range[1]);
                float cellU = (float) Math.floor(u0);
                for (float v0 = range[2]; v0 < range[3]; v0 = (float) Math.floor(v0) + 1.0F) {
                    float v1 = Math.min((float) Math.floor(v0) + 1.0F, range[3]);
                    float cellV = (float) Math.floor(v0);
                    // The sprite's top is v 0, and a face's v runs up it.
                    float[][] at = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                    for (float[] uv : at) {
                        float[] corner = face.at(b, uv[0], uv[1] - (face.side ? lift : 0.0F));
                        buffer.addVertex(pose, corner[0] - TILE_X, corner[1], corner[2] - TILE_Z).setColor(-1)
                                .setUv(sprite.getU(uv[0] - cellU), sprite.getV(1.0F - (uv[1] - cellV)))
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                                .setNormal(pose, face.normalX, face.normalY, face.normalZ);
                    }
                }
            }
        }
    }

    /**
     * A box's six faces, each with the way it is tiled: u and v as functions of the box's
     * coordinates, and back again.
     */
    private enum Face {
        /** +x: u runs along -z, as seen from outside it. */
        EAST(1, 0, 0, true),
        /** -x: u runs along z. */
        WEST(-1, 0, 0, true),
        /** +z: u runs along x. */
        SOUTH(0, 0, 1, true),
        /** -z: u runs along -x. */
        NORTH(0, 0, -1, true),
        /** +y: u along x, v along -z. */
        UP(0, 1, 0, false),
        /** -y: as the top. */
        DOWN(0, -1, 0, false);

        final float normalX;
        final float normalY;
        final float normalZ;
        /** Whether it is a side, whose v runs up it. */
        final boolean side;

        Face(int x, int y, int z, boolean side) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
            this.side = side;
        }

        /** The u and v it spans on {@code b}, from and to, its v raised by {@code lift}. */
        float[] uvRange(CarBody.Box b, float lift) {
            float x0 = (float) b.minX();
            float x1 = (float) b.maxX();
            float y0 = (float) b.minY() + lift;
            float y1 = (float) b.maxY() + lift;
            float z0 = (float) b.minZ();
            float z1 = (float) b.maxZ();
            return switch (this) {
                case EAST -> new float[] {-z1, -z0, y0, y1};
                case WEST -> new float[] {z0, z1, y0, y1};
                case SOUTH -> new float[] {x0, x1, y0, y1};
                case NORTH -> new float[] {-x1, -x0, y0, y1};
                case UP, DOWN -> new float[] {x0, x1, -z1, -z0};
            };
        }

        /** The point of the face on {@code b} at {@code u} and {@code v}, v up the side unraised. */
        float[] at(CarBody.Box b, float u, float v) {
            float x0 = (float) b.minX();
            float x1 = (float) b.maxX();
            float y0 = (float) b.minY();
            float y1 = (float) b.maxY();
            float z0 = (float) b.minZ();
            float z1 = (float) b.maxZ();
            return switch (this) {
                case EAST -> new float[] {x1, v, -u};
                case WEST -> new float[] {x0, v, u};
                case SOUTH -> new float[] {u, v, z1};
                case NORTH -> new float[] {-u, v, z0};
                case UP -> new float[] {u, y1, -v};
                case DOWN -> new float[] {u, y0, -v};
            };
        }

        /** Its four corners on {@code b}, round it. */
        float[][] corners(CarBody.Box b) {
            float[] r = uvRange(b, 0.0F);
            return new float[][] {at(b, r[0], r[2]), at(b, r[1], r[2]), at(b, r[1], r[3]), at(b, r[0], r[3])};
        }
    }
}
