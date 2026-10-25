package com.flansmod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.function.Consumer;

/** Adds optical layers to an already copied world image without entering the world renderer. */
public final class ScopeImageComposer {
    private static RenderTarget target;
    private static int sourceFramebuffer;
    private ScopeImageComposer() { }

    public static void compose(int texture, int width, int height, Consumer<RenderTarget> overlays) {
        if (texture <= 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Empty scope image");
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (var writes = new ScopeWorldTexture.TargetWrites()) {
            if (target == null) target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            else if (target.viewWidth != width || target.viewHeight != height) target.resize(width, height, Minecraft.ON_OSX);
            if (sourceFramebuffer == 0) sourceFramebuffer = GL30.glGenFramebuffers();
            target.bindWrite(true);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete scope image source");
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            target.bindWrite(true);
            overlays.accept(target);
            // Copy only after every layer is uploaded. The source image is never sampled while
            // attached to the framebuffer being drawn into, avoiding a texture feedback loop.
            target.bindWrite(false);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(binding);
            RenderSystem.activeTexture(active);
        }
    }

    public static void release() {
        if (target != null) { target.destroyBuffers(); target = null; }
        if (sourceFramebuffer != 0) { GlStateManager._glDeleteFramebuffers(sourceFramebuffer); sourceFramebuffer = 0; }
    }
}
