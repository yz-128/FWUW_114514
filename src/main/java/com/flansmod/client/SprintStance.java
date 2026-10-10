package com.flansmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Frame-interpolated sprint progress for the 1.7.10 pack's sprint vectors. */
public final class SprintStance
{
    private SprintStance() { }
    private static float progress, lastProgress;
    private static int stanceTimer;

    /** Retain the current firing suppression while the recoil settles. */
    public static void onShot() { stanceTimer = 20; }

    /** FMUM RunPoseTime controls both entering and leaving the pose. */
    public static void tick(boolean carrying, float length)
    {
        lastProgress = progress;
        float step = 1F / Math.max(1F, length);
        progress = Mth.clamp(progress + (carrying && stanceTimer == 0 ? step : -step), 0F, 1F);
        if(stanceTimer > 0) stanceTimer--;
    }

    public static float blend(float partialTick) { return lastProgress + (progress - lastProgress) * partialTick; }
    public static boolean suppressedByShot() { return stanceTimer > 0; }
    public static void reset() { progress = lastProgress = 0F; stanceTimer = 0; }
    public static boolean carrying()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isSprinting();
    }
}
