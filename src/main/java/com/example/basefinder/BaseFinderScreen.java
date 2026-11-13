package com.example.basefinder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BaseFinderScreen extends Screen {
    private List<Scanner.Cluster> clusters = List.of();

    public BaseFinderScreen() { super(Text.literal("BaseFinder")); }

    private String dim() { return Scanner.dimOf(MinecraftClient.getInstance().world); }

    @Override
    protected void init() {
        Config c = BaseFinderMod.CFG;
        int lx = 20, y = 40, w = 150, h = 20, gap = 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Scanner: " + (c.enabled ? "ON" : "OFF")), b -> {
            c.enabled = !c.enabled; clearAndInit(); }).dimensions(lx, y, w, h).build());
        y += gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("Chat alerts: " + (c.notify ? "ON" : "OFF")), b -> {
            c.notify = !c.notify; clearAndInit(); }).dimensions(lx, y, w, h).build());
        y += gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            c.minScore = Math.max(3, c.minScore - 3); clearAndInit(); }).dimensions(lx, y, 20, h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            c.minScore += 3; clearAndInit(); }).dimensions(lx + 130, y, 20, h).build());
        y += gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            c.chunksPerTick = Math.max(1, c.chunksPerTick - 1); clearAndInit(); }).dimensions(lx, y, 20, h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            c.chunksPerTick = Math.min(32, c.chunksPerTick + 1); clearAndInit(); }).dimensions(lx + 130, y, 20, h).build());
        y += gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop tracking"), b -> {
            BaseFinderMod.target = null; }).dimensions(lx, y, w, h).build());
        y += gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("Export results to file"), b -> export())
                .dimensions(lx, y, w, h).build());
        y += gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear scan data"), b -> {
            BaseFinderMod.SCANNER.clearCurrent(dim()); clearAndInit(); }).dimensions(lx, y, w, h).build());

        // Results list: top 10 clusters, click to track.
        clusters = BaseFinderMod.SCANNER.clusters(dim(), c.minScore);
        int rx = 190, ry = 40, rw = Math.max(150, width - rx - 20);
        for (int i = 0; i < Math.min(10, clusters.size()); i++) {
            Scanner.Cluster cl = clusters.get(i);
            double dx = cl.x() - client.player.getX(), dz = cl.z() - client.player.getZ();
            String label = "#" + (i + 1) + "  X " + cl.x() + "  Z " + cl.z() + "  | score " + cl.score()
                    + " | " + (int) Math.sqrt(dx * dx + dz * dz) + "m";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                BaseFinderMod.target = new int[]{cl.x(), cl.z()};
                close();
            }).dimensions(rx, ry + i * 24, rw, h).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> clearAndInit())
                .dimensions(rx, ry + 10 * 24 + 4, 80, h).build());
    }

    private void export() {
        try {
            StringBuilder sb = new StringBuilder("dimension " + dim() + "\n");
            for (Scanner.Cluster c : clusters)
                sb.append("X ").append(c.x()).append(" Z ").append(c.z()).append(" score ").append(c.score())
                  .append(" chunks ").append(c.chunks()).append(" sample ").append(c.best()).append('\n');
            Path p = FabricLoader.getInstance().getGameDir().resolve("basefinder_results.txt");
            Files.writeString(p, sb.toString());
            client.player.sendMessage(Text.literal("[BaseFinder] Saved to " + p), false);
        } catch (Exception e) {
            client.player.sendMessage(Text.literal("[BaseFinder] Export failed: " + e.getMessage()), false);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Config c = BaseFinderMod.CFG;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("BaseFinder  (Right Shift to close)"), width / 2, 12, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Min base score: " + c.minScore), 46, 40 + 24 * 2 + 6, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Chunks/tick: " + c.chunksPerTick), 46, 40 + 24 * 3 + 6, 0xFFFFFFFF);
        if (clusters.isEmpty())
            ctx.drawTextWithShadow(textRenderer, Text.literal("No bases found yet. Fly/travel to load new chunks."), 190, 46, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() { return false; }
}
