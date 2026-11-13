package com.example.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BaseFinderMod implements ClientModInitializer {
    public static final Config CFG = new Config();
    public static final Scanner SCANNER = new Scanner();
    /** Tracked target (block X/Z), or null. */
    public static int[] target = null;

    private boolean wasDown;
    private int tick;

    @Override
    public void onInitializeClient() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> SCANNER.queue(world, chunk.getPos()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { SCANNER.clear(); target = null; });
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        // Right Shift toggle, polled directly so it works on every 1.21.x input API.
        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && mc.world != null) {
            if (mc.currentScreen instanceof BaseFinderScreen) mc.setScreen(null);
            else if (mc.currentScreen == null) mc.setScreen(new BaseFinderScreen());
        }
        wasDown = down;

        SCANNER.tick(mc);

        if (target != null && mc.player != null && ++tick % 10 == 0) {
            double dx = target[0] + 0.5 - mc.player.getX(), dz = target[1] + 0.5 - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double want = Math.toDegrees(Math.atan2(-dx, dz));          // MC yaw convention
            double rel = ((want - mc.player.getYaw()) % 360 + 540) % 360 - 180;
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            String arrow = arrows[(int) Math.floorMod(Math.round(rel / 45.0), 8)];
            mc.player.sendMessage(Text.literal("Tracking base " + arrow + "  " + (int) dist + " blocks  (X "
                    + target[0] + " Z " + target[1] + ")"), true);
            if (dist < 24) target = null;
        }
    }
}
