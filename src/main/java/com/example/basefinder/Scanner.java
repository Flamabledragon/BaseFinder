package com.example.basefinder;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/** Scores chunks by how many player-made-looking blocks they contain, then groups neighbouring chunks into bases. */
public class Scanner {
    public record Job(String dim, int x, int z) {}
    public record ChunkScore(int score, int count, BlockPos best) {}
    public record Cluster(int score, int chunks, int x, int z, BlockPos best) {}

    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Map<String, Map<Long, ChunkScore>> results = new HashMap<>();
    private final Set<String> notified = new HashSet<>();
    private int tick;

    public static String dimOf(ClientWorld w) { return w.getRegistryKey().getValue().toString(); }

    public void queue(ClientWorld w, ChunkPos p) {
        queue.add(new Job(dimOf(w), p.x, p.z));
    }

    public void clear() { queue.clear(); results.clear(); notified.clear(); }

    public void clearCurrent(String dim) { results.remove(dim); notified.clear(); }

    public static int weight(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof ShulkerBoxBlock) return 6;
        if (b == Blocks.ENDER_CHEST || b == Blocks.BEACON) return 5;
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return 3;
        if (b instanceof BedBlock) return 2;
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER
                || b == Blocks.CRAFTING_TABLE || b == Blocks.ENCHANTING_TABLE || b == Blocks.ANVIL
                || b == Blocks.BREWING_STAND || b == Blocks.SMITHING_TABLE) return 2;
        if (b == Blocks.HOPPER || b == Blocks.DROPPER || b == Blocks.DISPENSER || b == Blocks.OBSERVER
                || b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.TNT
                || b == Blocks.LECTERN || b == Blocks.REPEATER || b == Blocks.COMPARATOR) return 1;
        return 0;
    }

    public void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null || !BaseFinderMod.CFG.enabled) return;
        String dim = dimOf(world);
        int budget = BaseFinderMod.CFG.chunksPerTick;
        while (budget-- > 0 && !queue.isEmpty()) {
            Job j = queue.poll();
            if (!j.dim().equals(dim)) continue;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(j.x(), j.z());
            if (chunk != null) scan(dim, chunk);
        }
        if (BaseFinderMod.CFG.notify && ++tick % 40 == 0) announce(mc, dim);
    }

    private void scan(String dim, WorldChunk chunk) {
        int score = 0, count = 0, bestW = 0;
        BlockPos best = null;
        ChunkSection[] sections = chunk.getSectionArray();
        int baseY = chunk.getBottomSectionCoord();
        ChunkPos cp = chunk.getPos();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(s -> weight(s) > 0)) continue;
            int y0 = (baseY + i) * 16;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                int w = weight(sec.getBlockState(x, y, z));
                if (w > 0) {
                    score += w; count++;
                    if (w > bestW) { bestW = w; best = new BlockPos(cp.getStartX() + x, y0 + y, cp.getStartZ() + z); }
                }
            }
        }
        Map<Long, ChunkScore> map = results.computeIfAbsent(dim, k -> new HashMap<>());
        if (score > 0) map.put(cp.toLong(), new ChunkScore(score, count, best));
        else map.remove(cp.toLong());
    }

    /** Connected components (8-neighbour) of scored chunks, filtered by min score, sorted best first. */
    public List<Cluster> clusters(String dim, int minScore) {
        Map<Long, ChunkScore> map = results.getOrDefault(dim, Map.of());
        Set<Long> seen = new HashSet<>();
        List<Cluster> out = new ArrayList<>();
        for (Long start : map.keySet()) {
            if (!seen.add(start)) continue;
            int total = 0, n = 0, bestW = -1;
            double sx = 0, sz = 0;
            BlockPos best = null;
            ArrayDeque<Long> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                long cur = stack.pop();
                ChunkScore cs = map.get(cur);
                ChunkPos p = new ChunkPos(cur);
                total += cs.score(); n++;
                sx += (p.x * 16 + 8.0) * cs.score(); sz += (p.z * 16 + 8.0) * cs.score();
                if (cs.score() > bestW) { bestW = cs.score(); best = cs.best(); }
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    long nb = ChunkPos.toLong(p.x + dx, p.z + dz);
                    if (map.containsKey(nb) && seen.add(nb)) stack.push(nb);
                }
            }
            if (total >= minScore) out.add(new Cluster(total, n, (int) (sx / total), (int) (sz / total), best));
        }
        out.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return out;
    }

    private void announce(MinecraftClient mc, String dim) {
        for (Cluster c : clusters(dim, BaseFinderMod.CFG.minScore)) {
            String key = dim + ":" + (c.x() >> 6) + ":" + (c.z() >> 6);
            if (notified.add(key) && mc.player != null) {
                mc.player.sendMessage(Text.literal("[BaseFinder] Possible base near X " + c.x() + " Z " + c.z()
                        + " (score " + c.score() + ", " + c.chunks() + " chunks)"), false);
            }
        }
    }
}
