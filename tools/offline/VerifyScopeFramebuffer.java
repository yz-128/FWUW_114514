import com.flansmod.client.render.ScopeWorldTexture;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Actual production colour copy between independent GPU targets; an invisible GL context, no game. */
public final class VerifyScopeFramebuffer {
    private static int checks;
    private static void check(boolean value,String label) {checks++;if(!value)throw new AssertionError(label);}
    private static int pixel(java.nio.ByteBuffer pixels,int width,int x,int y,int channel) {
        return pixels.get((y*width+x)*4+channel)&255;
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();RenderSystem.initRenderThread();
        check(GLFW.glfwInit(),"GLFW initialized");GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(64,48,"offline scope framebuffer verification",0,0);
        check(window!=0,"invisible GL context created");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        System.out.println("GPU="+GL11.glGetString(GL11.GL_RENDERER));
        TextureTarget world=null,scope=null;
        try {
            world=new TextureTarget(64,48,true,false);scope=new TextureTarget(64,48,true,false);
            var textureField=ScopeWorldTexture.class.getDeclaredField("TEXTURE");textureField.setAccessible(true);
            AbstractTexture texture=(AbstractTexture)textureField.get(null);
            for(int[] size:new int[][]{{64,48},{128,32},{32,128},{64,48}}) {
                world.resize(size[0],size[1],false);scope.resize(size[0],size[1],false);
                world.bindWrite(true);RenderSystem.clearColor(1,0,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
                scope.bindWrite(true);RenderSystem.clearColor(0,0,1,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,size[0]/2,size[1]/2);
                RenderSystem.clearColor(0,1,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);GL11.glDisable(GL11.GL_SCISSOR_TEST);
                int sentinel=GL11.glGenTextures();RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(sentinel);
                RenderSystem.activeTexture(GL13.GL_TEXTURE3);
                ScopeWorldTexture.copyColor(size[0],size[1]);
                check(ScopeWorldTexture.available(),"fresh scope copy available");
                check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE3,"active texture unit preserved");
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinel,"previous texture binding preserved");
                RenderSystem.bindTexture(texture.getId());
                check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==size[0],"copy width follows target resize");
                check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==size[1],"copy height follows target resize");
                var pixels=BufferUtils.createByteBuffer(size[0]*size[1]*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                check(pixel(pixels,size[0],1,1,1)==255,"bottom-left quadrant retains framebuffer orientation");
                check(pixel(pixels,size[0],size[0]-2,size[1]-2,2)==255,"upper-right quadrant comes from scope target");
                world.bindWrite(true);var original=BufferUtils.createByteBuffer(4);GL11.glReadPixels(1,1,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,original);
                check((original.get(0)&255)==255 && (original.get(2)&255)==0,"ordinary world is unchanged by independent scope copy");
                check(GL11.glGetError()==GL11.GL_NO_ERROR,"no GPU error after copy and resize");
                scope.bindWrite(true);ScopeWorldTexture.copySquareColor(size[0],size[1]);
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture.getId());
                int square=Math.min(size[0],size[1]);
                check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==square,"native scope capture square width");
                check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==square,"native scope capture square height");
                var cropped=BufferUtils.createByteBuffer(square*square*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,cropped);
                check(pixel(cropped,square,1,1,1)==255 && pixel(cropped,square,square-2,square-2,2)==255,"central crop orientation, landscape and portrait");
                GL11.glDeleteTextures(sentinel);
            }
            texture.load(null);
            check(!ScopeWorldTexture.available(),"resource reload invalidates the old optical image");
            scope.bindWrite(true);RenderSystem.clearColor(0,1,0,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
            ScopeWorldTexture.copyColor(64,48);
            check(ScopeWorldTexture.available(),"optical image can be recreated after resource reload");
            var targetField=ScopeWorldTexture.class.getDeclaredField("scopeTarget");targetField.setAccessible(true);targetField.set(null,scope);
            ScopeWorldTexture.logout(null);scope=null;
            check(!ScopeWorldTexture.available(),"leaving a world invalidates the last scope image");
            check(targetField.get(null)==null,"leaving a world frees the secondary target");
            texture.releaseId();
        } finally {
            if(scope!=null)scope.destroyBuffers();if(world!=null)world.destroyBuffers();
            GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
        System.out.println("VerifyScopeFramebuffer: "+checks+" GPU checks passed; no Minecraft window or world started");
    }
}
