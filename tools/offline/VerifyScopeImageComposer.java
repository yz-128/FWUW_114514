import com.flansmod.client.render.ScopeImageComposer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Actual production image composition in an invisible GL context; no game or world starts.
 * Deliberately enters with distinct caller read/draw targets, restricted writes and texture unit 3.
 * Callback changes only the write/clear state covered by the composer's TargetWrites contract. */
public final class VerifyScopeImageComposer {
    private static int checks;
    private static void check(boolean ok,String label) {checks++;if(!ok)throw new AssertionError(label);}
    private static int[] ints(int property,int count) {int[] value=new int[count];GL11.glGetIntegerv(property,value);return value;}
    private static byte[] rgbaMask() {
        var mask=BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
        byte[] value=new byte[4];mask.get(value);return value;
    }
    private static void fullWrites() {
        RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
    }
    private static final class State {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int[] viewport=ints(GL11.GL_VIEWPORT,4),box=ints(GL11.GL_SCISSOR_BOX,4);
        final boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final byte[] color=rgbaMask();
        final float[] clear=new float[4];
        final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        final int texture0,texture3;
        State() {
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);texture0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(GL13.GL_TEXTURE3);texture3=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(active);
        }
        void verify(String label) {
            check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==draw,label+" restores caller draw framebuffer");
            check(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==read,label+" restores independent caller read framebuffer");
            check(Arrays.equals(ints(GL11.GL_VIEWPORT,4),viewport),label+" restores caller viewport");
            check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)==scissor,label+" restores scissor enable");
            check(Arrays.equals(ints(GL11.GL_SCISSOR_BOX,4),box),label+" restores scissor box");
            check(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)==depth,label+" restores depth write mask");
            check(Arrays.equals(rgbaMask(),color),label+" restores every color write channel");
            float[] now=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,now);
            check(Arrays.equals(now,clear),label+" restores clear color");
            check(GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE)==clearDepth,label+" restores clear depth");
            check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==active,label+" restores active texture unit");
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==texture0,label+" restores texture unit zero binding");
            RenderSystem.activeTexture(GL13.GL_TEXTURE3);
            check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==texture3,label+" preserves caller texture unit three binding");
            RenderSystem.activeTexture(active);
        }
    }
    private static byte[] pixels(int framebuffer,int width,int height) {
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer);
            var data=BufferUtils.createByteBuffer(width*height*4);
            GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
            byte[] result=new byte[data.capacity()];data.get(result);return result;
        } finally {GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);}
    }
    private static byte[] texturePixels(int texture,int width,int height) {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.bindTexture(texture);var data=BufferUtils.createByteBuffer(width*height*4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
            byte[] result=new byte[data.capacity()];data.get(result);return result;
        } finally {RenderSystem.bindTexture(binding);RenderSystem.activeTexture(active);}
    }
    private static byte[] source(int texture,int width,int height) {
        var image=BufferUtils.createByteBuffer(width*height*4);
        // Asymmetric per-pixel color makes an accidental flip, crop or stale resize visible.
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)
            image.put((byte)(17+x*3)).put((byte)(31+y*2)).put((byte)(73+(x+y)%19)).put((byte)255);
        image.flip();byte[] original=new byte[image.remaining()];image.get(original);image.rewind();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,image);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        return original;
    }
    private static void callerState(TextureTarget draw,TextureTarget read,int sentinel0,int sentinel3,boolean scissor) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read.frameBufferId);
        RenderSystem.viewport(3,4,41,29);RenderSystem.enableScissor(2,3,5,7);
        if(!scissor)RenderSystem.disableScissor();
        RenderSystem.depthMask(false);RenderSystem.colorMask(false,true,false,false);
        RenderSystem.clearColor(.13F,.27F,.39F,.51F);RenderSystem.clearDepth(.375);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(sentinel0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE3);RenderSystem.bindTexture(sentinel3);
    }
    private static final class CallbackFailure extends RuntimeException { }
    private static void composeCase(TextureTarget draw,TextureTarget read,int texture,int sentinel0,int sentinel3,
                                    int width,int height,boolean scissor,boolean fail) {
        String label=width+"x"+height+" scissor="+scissor+" callbackFailure="+fail;
        byte[] original=source(texture,width,height),drawBefore=pixels(draw.frameBufferId,64,48),readBefore=pixels(read.frameBufferId,64,48);
        callerState(draw,read,sentinel0,sentinel3,scissor);var before=new State();boolean[] invoked={false};
        try {
            ScopeImageComposer.compose(texture,width,height,target->{
                invoked[0]=true;
                check(target.width==width&&target.height==height,label+" staging target follows actual image dimensions");
                check(target.getColorTextureId()!=texture&&target.frameBufferId!=draw.frameBufferId,label+" callback writes independent staging texture");
                check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==target.frameBufferId,label+" callback draw target bound");
                check(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==target.frameBufferId,label+" callback read target bound");
                check(Arrays.equals(ints(GL11.GL_VIEWPORT,4),new int[]{0,0,width,height}),label+" full image viewport supplied");
                check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),label+" caller write restrictions lifted");
                check(Arrays.equals(rgbaMask(),new byte[]{1,1,1,1}),label+" all channels writable during image composition");
                check(Arrays.equals(pixels(target.frameBufferId,width,height),original),label+" staging world copy preserves every pixel and orientation");
                RenderSystem.enableScissor(1,1,2,2);RenderSystem.clearColor(1,0,1,1);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
                RenderSystem.depthMask(false);RenderSystem.colorMask(true,false,true,false);RenderSystem.clearDepth(.625);
                if(fail)throw new CallbackFailure();
            });
            check(!fail,label+" expected callback exception must propagate");
        } catch(CallbackFailure expected) {check(fail,label+" original callback exception propagated");}
        check(invoked[0],label+" invokes actual overlay callback");before.verify(label);
        byte[] composed=texturePixels(texture,width,height),expected=original.clone();
        if(!fail)for(int y=1;y<3;y++)for(int x=1;x<3;x++) {
            int p=(y*width+x)*4;expected[p]=(byte)255;expected[p+1]=0;expected[p+2]=(byte)255;expected[p+3]=(byte)255;
        }
        check(Arrays.equals(composed,expected),label+(fail?" failed composition leaves original texture untouched":" only selected 2x2 overlay pixels change"));
        check(Arrays.equals(pixels(draw.frameBufferId,64,48),drawBefore),label+" main draw image is byte-for-byte unchanged");
        check(Arrays.equals(pixels(read.frameBufferId,64,48),readBefore),label+" independent caller read image is unchanged");
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);
        check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_NEAREST
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_NEAREST,label+" image sampler filters preserved");
        check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)==GL12.GL_CLAMP_TO_EDGE
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)==GL12.GL_CLAMP_TO_EDGE,label+" image wrap modes preserved");
        check(GL11.glGetError()==GL11.GL_NO_ERROR,label+" no OpenGL error");
    }
    private static void invalidCases(TextureTarget draw,TextureTarget read,int texture,int sentinel0,int sentinel3) {
        callerState(draw,read,sentinel0,sentinel3,true);var before=new State();
        for(int[] args:new int[][]{{0,16,16},{texture,0,16},{texture,16,-1}}) {
            boolean rejected=false;
            try {ScopeImageComposer.compose(args[0],args[1],args[2],target->{throw new AssertionError("invalid input callback");});}
            catch(IllegalArgumentException expected) {rejected=true;}
            check(rejected,"empty image/texture is rejected before composition");before.verify("invalid input");
        }
        int empty=GL11.glGenTextures();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(empty);
        callerState(draw,read,sentinel0,sentinel3,false);before=new State();
        try {
            boolean rejected=false;
            try {ScopeImageComposer.compose(empty,16,16,target->{throw new AssertionError("incomplete source callback");});}
            catch(IllegalStateException expected) {rejected=true;}
            check(rejected,"source texture without storage rejects incomplete framebuffer");before.verify("incomplete source");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"incomplete source restoration has no GL error");
        } finally {GL11.glDeleteTextures(empty);}
    }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();RenderSystem.initRenderThread();
        check(GLFW.glfwInit(),"GLFW initialized");GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(64,48,"offline scope image composition",0,0);
        check(window!=0,"invisible GPU context created");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        System.out.println("GPU="+GL11.glGetString(GL11.GL_RENDERER));
        TextureTarget draw=null,read=null;int texture=0,sentinel0=0,sentinel3=0;
        try {
            fullWrites();draw=new TextureTarget(64,48,false,false);read=new TextureTarget(64,48,false,false);
            draw.bindWrite(true);RenderSystem.clearColor(.9F,.1F,.2F,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
            read.bindWrite(true);RenderSystem.clearColor(.1F,.8F,.3F,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
            texture=GL11.glGenTextures();sentinel0=GL11.glGenTextures();sentinel3=GL11.glGenTextures();
            for(int[] size:new int[][]{{48,32},{32,48},{7,5},{64,48}})
                for(boolean scissor:new boolean[]{false,true})for(boolean fail:new boolean[]{false,true})
                    composeCase(draw,read,texture,sentinel0,sentinel3,size[0],size[1],scissor,fail);
            invalidCases(draw,read,texture,sentinel0,sentinel3);
            ScopeImageComposer.release();composeCase(draw,read,texture,sentinel0,sentinel3,17,13,true,false);
        } finally {
            fullWrites();ScopeImageComposer.release();
            if(draw!=null)draw.destroyBuffers();if(read!=null)read.destroyBuffers();
            GL11.glDeleteTextures(texture);GL11.glDeleteTextures(sentinel0);GL11.glDeleteTextures(sentinel3);
            GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
        System.out.println("VerifyScopeImageComposer: "+checks+" GPU checks passed; exact pixels, resize, independent caller targets, normal/exceptional state restoration; no Minecraft window or world started");
    }
}
