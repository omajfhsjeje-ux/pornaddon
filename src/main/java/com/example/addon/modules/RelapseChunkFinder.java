package com.relapse.debug.modules;

import com.relapse.debug.RelapseDebug;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public class RelapseChunkFinder extends Module {
    public enum MarkMode { Pillar, Chunk }

    private final SettingGroup sgFrames = settings.createGroup("Item Frames");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgTracer = settings.createGroup("Tracers");

    // ---- Item frames ----
    private final Setting<Boolean> normalFrames = sgFrames.add(new BoolSetting.Builder()
        .name("normal-frames").description("Mark normal item frames.").defaultValue(true).build());

    private final Setting<Boolean> glowFrames = sgFrames.add(new BoolSetting.Builder()
        .name("glow-frames").description("Mark glow item frames.").defaultValue(true).build());

    private final Setting<Boolean> undergroundOnly = sgFrames.add(new BoolSetting.Builder()
        .name("underground-only").description("Only mark frames that are underground.").defaultValue(true).build());

    private final Setting<Integer> maxY = sgFrames.add(new IntSetting.Builder()
        .name("max-y").description("Frames at or below this Y level count as underground.")
        .defaultValue(50).range(-64, 320).sliderRange(-64, 128)
        .visible(undergroundOnly::get).build());

    private final Setting<Boolean> noSkyOnly = sgFrames.add(new BoolSetting.Builder()
        .name("require-no-sky").description("Also require that the frame has no direct sky access.")
        .defaultValue(false).visible(undergroundOnly::get).build());

    private final Setting<Boolean> remember = sgFrames.add(new BoolSetting.Builder()
        .name("remember").description("Keep marking chunks after they unload (cleared when you leave the server).")
        .defaultValue(true).build());

    // ---- Render ----
    private final Setting<MarkMode> mode = sgRender.add(new EnumSetting.Builder<MarkMode>()
        .name("mode").description("Pillar = full-height column of the chunk. Chunk = the 16x16x16 chunk section containing the frame.")
        .defaultValue(MarkMode.Pillar).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How the box is rendered.").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> normalColor = sgRender.add(new ColorSetting.Builder()
        .name("normal-color").description("Color for chunks with normal item frames.")
        .defaultValue(new SettingColor(255, 170, 0, 255)).build());

    private final Setting<SettingColor> glowColor = sgRender.add(new ColorSetting.Builder()
        .name("glow-color").description("Color for chunks with glow item frames.")
        .defaultValue(new SettingColor(0, 255, 200, 255)).build());

    private final Setting<Integer> fillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").description("Alpha of the box fill (0-255).")
        .defaultValue(40).range(0, 255).sliderRange(0, 255).build());

    private final Setting<Integer> lineAlpha = sgRender.add(new IntSetting.Builder()
        .name("line-alpha").description("Alpha of the box outline (0-255).")
        .defaultValue(200).range(0, 255).sliderRange(0, 255).build());

    // ---- Tracers ----
    private final Setting<Boolean> tracers = sgTracer.add(new BoolSetting.Builder()
        .name("tracers").description("Draw tracers to marked chunks.").defaultValue(false).build());

    private final Setting<Boolean> tracerUseFrameColor = sgTracer.add(new BoolSetting.Builder()
        .name("use-frame-color").description("Tracer uses the same color as the marker.")
        .defaultValue(true).visible(tracers::get).build());

    private final Setting<SettingColor> tracerColor = sgTracer.add(new ColorSetting.Builder()
        .name("tracer-color").description("Custom tracer color.")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .visible(() -> tracers.get() && !tracerUseFrameColor.get()).build());

    // chunk -> marker data
    private static class Entry {
        final BlockPos pos;
        boolean glow;
        Entry(BlockPos pos, boolean glow) { this.pos = pos; this.glow = glow; }
    }

    private final Map<Long, Entry> found = new HashMap<>();

    public RelapseChunkFinder() {
        super(RelapseDebug.CATEGORY, "relapse-chunk-finder",
            "Marks chunks containing item frames (normal / glow), optionally only underground.");
    }

    @Override
    public void onActivate() {
        found.clear();
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) {
        found.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;
        if (!remember.get()) found.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) continue;

            boolean glow = frame instanceof GlowItemFrameEntity;
            if (glow && !glowFrames.get()) continue;
            if (!glow && !normalFrames.get()) continue;

            BlockPos pos = frame.getBlockPos();

            if (undergroundOnly.get()) {
                if (pos.getY() > maxY.get()) continue;
                if (noSkyOnly.get() && mc.world.isSkyVisible(pos)) continue;
            }

            long key = new ChunkPos(pos).toLong();
            Entry existing = found.get(key);
            if (existing == null) found.put(key, new Entry(pos, glow));
            else if (glow) existing.glow = true; // glow takes color priority
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || found.isEmpty()) return;

        int bottom = mc.world.getBottomY();
        int top = bottom + mc.world.getHeight();

        for (Map.Entry<Long, Entry> e : found.entrySet()) {
            ChunkPos cp = new ChunkPos(e.getKey());
            Entry data = e.getValue();

            SettingColor base = data.glow ? glowColor.get() : normalColor.get();
            Color side = new Color(base.r, base.g, base.b, fillAlpha.get());
            Color line = new Color(base.r, base.g, base.b, lineAlpha.get());

            double x1 = cp.getStartX(), z1 = cp.getStartZ();
            double x2 = x1 + 16, z2 = z1 + 16;
            double y1, y2;

            if (mode.get() == MarkMode.Pillar) {
                y1 = bottom;
                y2 = top;
            } else {
                y1 = Math.floorDiv(data.pos.getY(), 16) * 16;
                y2 = y1 + 16;
            }

            event.renderer.box(x1, y1, z1, x2, y2, z2, side, line, shapeMode.get(), 0);

            if (tracers.get()) {
                Color tc = tracerUseFrameColor.get() ? line : tracerColor.get();
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    data.pos.getX() + 0.5, data.pos.getY() + 0.5, data.pos.getZ() + 0.5,
                    tc);
            }
        }
    }

    @Override
    public String getInfoString() {
        return found.isEmpty() ? null : String.valueOf(found.size());
    }
}
