// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.RailCurve;
import io.github._5thlayer.railworks.rail.RailGeometry;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The Rail between Arms, drawn as an inverted T swept along each Span's {@link RailCurve}: a flange
 * along the bottom, resting on the Pylon tops, and a web standing on it, from the Arm of this Pylon
 * to the other end of each Span it is the first end of, so exactly one end draws a Span. Only the
 * top segment of a column has an Arm, and it draws the Arm's cap and the bearing plate the Rail rests
 * on, so they follow the column as its Arm does, derived from the blocks.
 *
 * <p>The Rail is Riley's cement, with a steel running strip along the top of the web, at 16 px per
 * block, its texture tiled along the Span from its first end and running on through the curve. The
 * held Rail's slack and the Placement Preview's Span are a placement signal, not art: they keep
 * their flat colours.
 */
public final class SpanRenderer implements BlockEntityRenderer<BlockEntity, SpanRenderer.State> {

    /** Where the flange's underside rests on the top segment. */
    private static final double ATTACH_HEIGHT = RailGeometry.ATTACH_Y;
    /** The flat colours' four sides shaded apart. */
    private static final float[] SHADE = {1.0F, 0.75F, 0.55F, 0.85F};

    private static final SpriteId CEMENT = sprite("rail/cement");
    private static final SpriteId STEEL = sprite("steel");
    private static final SpriteId CAP = sprite("pylon/cap");
    /** How tall the steel running strip along the top of the web is. */
    private static final double STRIP_HEIGHT = 2.0 / 16.0;
    /** The Arm's cap and the bearing plate on it, in pixels of the top segment: from x, y, z to x, y, z. */
    private static final float[] CAP_BOX = {2, 11, 2, 14, 15, 14};
    private static final float[] PLATE_BOX = {3, 15, 3, 13, 16, 13};
    /**
     * How far the plate's top stands over the post's, so the two never share a plane: under the
     * Rail's {@link #GAP} over the Pylon top, so the flange still clears it.
     */
    private static final float PROUD = 0.0025F;

    /** Where the Rail rests on this block's own Arm, from its origin. */
    private static final Vec3 START = new Vec3(0.5, ATTACH_HEIGHT, 0.5);
    /** How far apart the Rail keeps faces that would otherwise share a plane. */
    private static final double GAP = 0.005;
    /** The shortest piece a curve ends in: a shorter remainder is run on into the piece before. */
    private static final double SHORTEST_PIECE = 1.0 / 64.0;

    private final SpriteGetter sprites;

    public SpanRenderer(SpriteGetter sprites) {
        this.sprites = sprites;
    }

    private static SpriteId sprite(String path) {
        return Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath(Railworks.MOD_ID, path));
    }

    /**
     * A curve sampled for drawing: where the flange's underside runs, relative to the drawing's
     * origin, the way the curve runs, and how far along it, at each point.
     */
    record Sweep(Vec3[] points, Vec3[] headings, double[] along) {
    }

    /**
     * {@code curve} sampled for drawing, its points less {@code origin}, a world position: a point at
     * each whole block it runs, measured along the curve, and at its end, so each piece between them
     * covers one tile of the texture or the part of one left at the end. A last piece shorter than
     * {@link #SHORTEST_PIECE} runs on from the one before instead.
     */
    static Sweep sweep(RailCurve curve, Vec3 origin) {
        double arc = curve.arcLength();
        int whole = (int) Math.floor(arc);
        int pieces = whole == 0 || arc - whole >= SHORTEST_PIECE ? whole + 1 : whole;
        Vec3[] points = new Vec3[pieces + 1];
        Vec3[] headings = new Vec3[pieces + 1];
        double[] along = new double[pieces + 1];
        for (int i = 0; i <= pieces; i++) {
            along[i] = i == pieces ? arc : i;
            // The curve is measured in its straight length, in equal steps along it.
            RailCurve.Pose pose = curve.at(arc == 0.0 ? 0.0 : along[i] * curve.length() / arc);
            points[i] = new Vec3(pose.x() + RailGeometry.ATTACH_X - origin.x, pose.y() + ATTACH_HEIGHT - origin.y,
                    pose.z() + RailGeometry.ATTACH_Z - origin.z);
            headings[i] = new Vec3(pose.dx(), pose.dy(), pose.dz());
        }
        return new Sweep(points, headings, along);
    }

    /** Where the flange's underside rests on a Pylon's top segment, in world coordinates. */
    static Vec3 attachPoint(BlockPos arm) {
        return new Vec3(arm.getX() + RailGeometry.ATTACH_X, arm.getY() + ATTACH_HEIGHT, arm.getZ() + RailGeometry.ATTACH_Z);
    }

    public static final class State extends BlockEntityRenderState {
        /** Whether it is the top segment of its column, which has the Arm. */
        boolean arm;
        /** Index-aligned: each Rail's curve, relative to this block's origin, and its light. */
        final List<Sweep> sweeps = new ArrayList<>();
        final List<Integer> lights = new ArrayList<>();
        /** The slack from this Arm to the player's hand or the aimed Pylon, relative to this block's origin. */
        @Nullable Vec3 slackEnd;
        Stringing.Tint slackTint = Stringing.Tint.HELD;
        int slackLight;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BlockEntity pylon, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pylon, state, partialTicks, cameraPosition, breakProgress);
        state.sweeps.clear();
        state.lights.clear();
        state.slackEnd = null;
        Level level = pylon.getLevel();
        BlockPos at = pylon.getBlockPos();
        state.arm = level != null && PylonColumn.isArm(level, at);
        if (!state.arm) {
            return;
        }
        Arm self = PylonColumn.arm(at);
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.SKY, at));
        HeldSpan.Aim aim = HeldSpan.current(level);
        for (Span span : ClientRails.network().all()) {
            // A Span's first end sorts first by position, so exactly one end draws it. One the held
            // Rail would cut is drawn by the orange slack instead.
            if (!span.a().equals(self) || (aim != null && aim.cuts(span.a(), span.b()))) {
                continue;
            }
            BlockPos to = PylonColumn.block(span.b());
            if (!level.isLoaded(to) || !PylonColumn.isArm(level, to)) {
                continue;
            }
            state.sweeps.add(sweep(ClientRails.network().curve(span), Vec3.atLowerCornerOf(at)));
            state.lights.add(light);
        }
        if (aim != null && aim.anchor().equals(at)) {
            state.slackEnd = aim.freeEnd(partialTicks).subtract(at.getX(), at.getY(), at.getZ());
            state.slackTint = aim.tint();
            state.slackLight = light;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.arm) {
            TextureAtlasSprite cap = sprites.get(CAP);
            TextureAtlasSprite cement = sprites.get(CEMENT);
            TextureAtlasSprite steel = sprites.get(STEEL);
            int light = state.lightCoords;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
                drawBox(pose, buffer, CAP_BOX, 0.0F, cap, light);
                drawBox(pose, buffer, PLATE_BOX, PROUD, steel, light);
                for (int i = 0; i < state.sweeps.size(); i++) {
                    drawTextured(pose, buffer, state.sweeps.get(i), state.lights.get(i), cement, steel);
                }
            });
        }
        Vec3 slack = state.slackEnd;
        if (slack != null) {
            float[] colour = colourOf(state.slackTint);
            int light = state.slackLight;
            collector.submitCustomGeometry(poseStack, RenderTypes.leash(),
                    (pose, buffer) -> drawSpan(pose.pose(), buffer, START, slack, light, 1.0F,
                            colour[0], colour[1], colour[2]));
        }
    }

    /** The slack's colour: leash brown while held, green, orange or red over a Pylon: string, cut or refused. */
    static float[] colourOf(Stringing.Tint tint) {
        return switch (tint) {
            case HELD -> new float[] {0.5F, 0.4F, 0.3F};
            case STRING -> new float[] {0.2F, 0.8F, 0.2F};
            case CUT -> new float[] {1.0F, 0.55F, 0.0F};
            case REFUSED -> new float[] {0.8F, 0.1F, 0.1F};
        };
    }

    /**
     * The Rail straight from {@code start} to {@code end}, which are where its flange's underside
     * rests: an inverted T of two prisms, the flange and the web on it, each one triangle strip.
     */
    static void drawSpan(Matrix4f pose, VertexConsumer buffer, Vec3 start, Vec3 end, int light, float fade,
                         float r, float g, float b) {
        Vec3 along = end.subtract(start);
        drawCurve(pose, buffer, new Sweep(new Vec3[] {start, end}, new Vec3[] {along, along},
                new double[] {0.0, along.length()}), light, fade, r, g, b);
    }

    /** The Rail along {@code sweep}: the inverted T as one pair of prisms for each piece of it. */
    static void drawCurve(Matrix4f pose, VertexConsumer buffer, Sweep sweep, int light, float fade,
                          float r, float g, float b) {
        // The flange's underside a hair over the Pylon top, and the web's bottom a hair down into the
        // flange, so no two faces share a plane, as CarBody keeps them; the flange's top, where a car
        // rides, and the web's top stay where RailGeometry has them.
        double flangeHeight = RailGeometry.FLANGE_HEIGHT - GAP;
        double webHeight = RailGeometry.WEB_HEIGHT + GAP;
        Vec3 flange = new Vec3(0, GAP + flangeHeight / 2.0, 0);
        Vec3 web = new Vec3(0, RailGeometry.FLANGE_HEIGHT - GAP + webHeight / 2.0, 0);
        Vec3[] points = sweep.points();
        Vec3[] headings = sweep.headings();
        Vec3[][] flanges = new Vec3[points.length][];
        Vec3[][] webs = new Vec3[points.length][];
        for (int i = 0; i < points.length; i++) {
            flanges[i] = corners(headings[i], RailGeometry.FLANGE_WIDTH / 2.0, flangeHeight / 2.0);
            webs[i] = corners(headings[i], RailGeometry.WEB_WIDTH / 2.0, webHeight / 2.0);
        }
        for (int i = 0; i + 1 < points.length; i++) {
            drawPrism(pose, buffer, points[i].add(flange), flanges[i], points[i + 1].add(flange), flanges[i + 1],
                    light, fade, r, g, b);
            drawPrism(pose, buffer, points[i].add(web), webs[i], points[i + 1].add(web), webs[i + 1],
                    light, fade, r, g, b);
        }
    }

    /**
     * The Rail along {@code sweep} in its textures: the flange and the web of cement, and the steel
     * running strip on the web, each a box swept from piece to piece, its ends closed. Along the
     * Rail a texture runs one tile to a block from the sweep's start; across a face it is laid at 16
     * px per block, a side's and an end's from the sprite's top down and a top's from its middle out.
     */
    private static void drawTextured(PoseStack.Pose pose, VertexConsumer buffer, Sweep sweep, int light,
                                     TextureAtlasSprite cement, TextureAtlasSprite steel) {
        double top = RailGeometry.FLANGE_HEIGHT + RailGeometry.WEB_HEIGHT;
        double flangeHalf = RailGeometry.FLANGE_WIDTH / 2.0;
        double webHalf = RailGeometry.WEB_WIDTH / 2.0;
        // The flange's underside a hair over the Pylon top and the web's foot a hair into the flange,
        // as drawCurve keeps them.
        sweptBox(pose, buffer, sweep, flangeHalf, GAP, RailGeometry.FLANGE_HEIGHT, cement, light, true, true);
        sweptBox(pose, buffer, sweep, webHalf, RailGeometry.FLANGE_HEIGHT - GAP, top - STRIP_HEIGHT, cement, light,
                false, false);
        sweptBox(pose, buffer, sweep, webHalf, top - STRIP_HEIGHT, top, steel, light, true, false);
    }

    /**
     * A box of {@code halfWidth} either side of the sweep, from {@code bottom} to {@code top} over the
     * flange's underside, in {@code sprite}: its two sides and its two ends, with its top and its
     * bottom where they show.
     */
    private static void sweptBox(PoseStack.Pose pose, VertexConsumer buffer, Sweep sweep, double halfWidth,
                                 double bottom, double top, TextureAtlasSprite sprite, int light,
                                 boolean drawTop, boolean drawBottom) {
        Vec3[] points = sweep.points();
        Vec3 middle = new Vec3(0, (bottom + top) / 2.0, 0);
        Vec3[] centres = new Vec3[points.length];
        // Corners: 0 top right, 1 bottom right, 2 bottom left, 3 top left, seen running along the sweep.
        Vec3[][] corners = new Vec3[points.length][];
        for (int i = 0; i < points.length; i++) {
            centres[i] = points[i].add(middle);
            Vec3[] around = corners(sweep.headings()[i], halfWidth, (top - bottom) / 2.0);
            corners[i] = new Vec3[around.length];
            for (int c = 0; c < around.length; c++) {
                corners[i][c] = centres[i].add(around[c]);
            }
        }
        float height = (float) (top - bottom);
        float across0 = (float) (0.5 - halfWidth);
        float across1 = (float) (0.5 + halfWidth);
        for (int i = 0; i + 1 < points.length; i++) {
            // A piece the remainder ran on into runs a hair over a tile; its texture stops at the tile's edge.
            float u1 = (float) Math.min(1.0, sweep.along()[i + 1] - sweep.along()[i]);
            Vec3[] a = corners[i];
            Vec3[] b = corners[i + 1];
            Vec3 inside = centres[i].add(centres[i + 1]).scale(0.5);
            quad(pose, buffer, sprite, light, inside, a[0], b[0], b[1], a[1], 0.0F, u1, 0.0F, height);
            quad(pose, buffer, sprite, light, inside, a[3], b[3], b[2], a[2], 0.0F, u1, 0.0F, height);
            if (drawTop) {
                quad(pose, buffer, sprite, light, inside, a[3], b[3], b[0], a[0], 0.0F, u1, across0, across1);
            }
            if (drawBottom) {
                quad(pose, buffer, sprite, light, inside, a[2], b[2], b[1], a[1], 0.0F, u1, across0, across1);
            }
        }
        int last = points.length - 1;
        Vec3[] f = corners[0];
        Vec3[] l = corners[last];
        quad(pose, buffer, sprite, light, centres[0].add(sweep.headings()[0].normalize().scale(GAP)),
                f[3], f[0], f[1], f[2], across0, across1, 0.0F, height);
        quad(pose, buffer, sprite, light, centres[last].subtract(sweep.headings()[last].normalize().scale(GAP)),
                l[0], l[3], l[2], l[1], across0, across1, 0.0F, height);
    }

    /**
     * One textured quad, {@code p0} to {@code p3} round it: {@code p0} at the sprite's {@code u0} and
     * {@code v0}, {@code p1} at {@code u1} and {@code v0}, {@code p2} at {@code u1} and {@code v1}, and
     * {@code p3} at {@code u0} and {@code v1}, u and v running from 0 to 1 across the sprite. Its
     * normal points away from {@code inside}, a point within the box it is a face of.
     */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light, Vec3 inside,
                             Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float u1, float v0, float v1) {
        Vec3 normal = p1.subtract(p0).cross(p3.subtract(p0)).normalize();
        if (normal.dot(p0.subtract(inside)) < 0.0) {
            normal = normal.reverse();
        }
        corner(pose, buffer, light, p0, sprite.getU(u0), sprite.getV(v0), normal);
        corner(pose, buffer, light, p1, sprite.getU(u1), sprite.getV(v0), normal);
        corner(pose, buffer, light, p2, sprite.getU(u1), sprite.getV(v1), normal);
        corner(pose, buffer, light, p3, sprite.getU(u0), sprite.getV(v1), normal);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer buffer, int light, Vec3 at, float u, float v,
                               Vec3 normal) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /**
     * A box on the top segment, {@code box} in pixels from x, y, z to x, y, z, in {@code sprite} at
     * 16 px per block, each face showing the part of the sprite it covers as a block model's face
     * does; its top raised by {@code proud}.
     */
    private static void drawBox(PoseStack.Pose pose, VertexConsumer buffer, float[] box, float proud,
                                TextureAtlasSprite sprite, int light) {
        float x0 = box[0] / 16.0F;
        float y0 = box[1] / 16.0F;
        float z0 = box[2] / 16.0F;
        float x1 = box[3] / 16.0F;
        float y1 = box[4] / 16.0F + proud;
        float z1 = box[5] / 16.0F;
        // The sprite's top is v 0: a side shows the rows its height covers, counted down from the
        // block's top, and the proud sliver none past it.
        float vTop = Math.max(0.0F, 1.0F - y1);
        float vFoot = 1.0F - y0;
        Vec3 inside = new Vec3((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0);
        Vec3 nwb = new Vec3(x0, y0, z0);
        Vec3 neb = new Vec3(x1, y0, z0);
        Vec3 swb = new Vec3(x0, y0, z1);
        Vec3 seb = new Vec3(x1, y0, z1);
        Vec3 nwt = new Vec3(x0, y1, z0);
        Vec3 net = new Vec3(x1, y1, z0);
        Vec3 swt = new Vec3(x0, y1, z1);
        Vec3 set = new Vec3(x1, y1, z1);
        quad(pose, buffer, sprite, light, inside, nwt, net, set, swt, x0, x1, z0, z1);
        quad(pose, buffer, sprite, light, inside, swb, seb, neb, nwb, x0, x1, 1.0F - z1, 1.0F - z0);
        quad(pose, buffer, sprite, light, inside, net, nwt, nwb, neb, 1.0F - x1, 1.0F - x0, vTop, vFoot);
        quad(pose, buffer, sprite, light, inside, swt, set, seb, swb, x0, x1, vTop, vFoot);
        quad(pose, buffer, sprite, light, inside, nwt, swt, swb, nwb, z0, z1, vTop, vFoot);
        quad(pose, buffer, sprite, light, inside, set, net, neb, seb, 1.0F - z1, 1.0F - z0, vTop, vFoot);
    }

    /** The four corners of a cross-section running along {@code along}, from its centre: its sides level, its top up. */
    private static Vec3[] corners(Vec3 along, double halfWidth, double halfHeight) {
        Vec3 unit = along.normalize();
        Vec3 up = Math.abs(unit.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = unit.cross(up).normalize().scale(halfWidth);
        Vec3 lift = side.cross(unit).normalize().scale(halfHeight);
        return new Vec3[] {
                side.add(lift), side.subtract(lift), side.reverse().subtract(lift), side.reverse().add(lift)
        };
    }

    /**
     * A prism from {@code start} to {@code end} (the centres of its two ends, each with its own
     * cross-section's corners, so pieces of a curve meet face to face), as one triangle strip around
     * its four sides.
     *
     * <p>The pipeline draws every prism of a frame into one buffer, so consecutive prisms join into one
     * strip, and the triangles between one prism's end and the next one's start would fill the space
     * between them. The strip therefore repeats its first and last vertex twice, as Wireworks'
     * {@code WireStrip} does: every triangle that spans two prisms is zero-area, and the even count
     * keeps the winding.
     */
    private static void drawPrism(Matrix4f pose, VertexConsumer buffer, Vec3 start, Vec3[] startCorners, Vec3 end,
                                  Vec3[] endCorners, int light, float fade, float r, float g, float b) {
        vertex(pose, buffer, r, g, b, fade, start.add(startCorners[0]), SHADE[0], light);
        vertex(pose, buffer, r, g, b, fade, start.add(startCorners[0]), SHADE[0], light);
        for (int i = 0; i <= startCorners.length; i++) {
            int corner = i % startCorners.length;
            float shade = SHADE[i % SHADE.length];
            vertex(pose, buffer, r, g, b, fade, start.add(startCorners[corner]), shade, light);
            vertex(pose, buffer, r, g, b, fade, end.add(endCorners[corner]), shade, light);
        }
        vertex(pose, buffer, r, g, b, fade, end.add(endCorners[0]), SHADE[0], light);
        vertex(pose, buffer, r, g, b, fade, end.add(endCorners[0]), SHADE[0], light);
    }

    private static void vertex(Matrix4f pose, VertexConsumer buffer, float r, float g, float b, float fade, Vec3 at,
                               float shade, int light) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(r * shade * fade, g * shade * fade, b * shade * fade, 1.0F)
                .setLight(light);
    }

    /** A Rail leaves the frustum long after the Pylon that draws it does. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return BlockEntityRenderer.super.getViewDistance() + (int) Math.ceil(SpanRules.lengthLimit());
    }

    @Override
    public AABB getRenderBoundingBox(BlockEntity pylon) {
        return new AABB(pylon.getBlockPos()).inflate(SpanRules.lengthLimit());
    }
}
