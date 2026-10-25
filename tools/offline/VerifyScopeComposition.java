import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Real PSO-1 mesh/atlas pixel composition in an invisible GPU context; synthetic world, no game. */
public final class VerifyScopeComposition {
    static final int W=1280,H=720;
    static int checks,program,vao,vbo;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static final class Capture implements MultiBufferSource,VertexConsumer {
        final List<float[]> vertices=new ArrayList<>();float[] current;
        public VertexConsumer getBuffer(RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){current=new float[]{x,y,z,0,0,1,1,1,1};vertices.add(current);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){current[5]=r/255F;current[6]=g/255F;current[7]=b/255F;current[8]=a/255F;return this;}
        public VertexConsumer setUv(float u,float v){current[3]=u;current[4]=v;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static int shader(int kind,String text) {
        int shader=GL20.glCreateShader(kind);GL20.glShaderSource(shader,text);GL20.glCompileShader(shader);
        check(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,"shader compile: "+GL20.glGetShaderInfoLog(shader));return shader;
    }
    static void initShader() {
        int vertex=shader(GL20.GL_VERTEX_SHADER,shaderSource("assets/minecraft/shaders/core/rendertype_entity_translucent_emissive.vsh"));
        int fragment=shader(GL20.GL_FRAGMENT_SHADER,shaderSource("assets/minecraft/shaders/core/rendertype_entity_translucent_emissive.fsh"));
        program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);
        GL20.glBindAttribLocation(program,0,"Position");GL20.glBindAttribLocation(program,1,"UV0");GL20.glBindAttribLocation(program,2,"Color");
        GL20.glBindAttribLocation(program,3,"UV1");GL20.glBindAttribLocation(program,4,"UV2");GL20.glBindAttribLocation(program,5,"Normal");
        GL20.glLinkProgram(program);check(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,"shader link");
        GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);
        vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();
    }
    private static String shaderSource(String path) {
        try(var stream=VerifyScopeComposition.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null)throw new IllegalStateException("Missing vanilla shader "+path);
            String source=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var matcher=java.util.regex.Pattern.compile("#moj_import <([^>]+)>").matcher(source);var result=new StringBuffer();
            while(matcher.find())matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(shaderSource("assets/minecraft/shaders/include/"+matcher.group(1))));
            matcher.appendTail(result);
            return path.contains("/include/")?result.toString().replaceAll("(?m)^\\s*#version[^\\r\\n]*", ""):result.toString();
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    static int texture(BufferedImage image) {
        int texture=GL11.glGenTextures();RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);
        var pixels=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int c=image.getRGB(x,y);pixels.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));
        }
        pixels.flip();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return texture;
    }
    static void draw(Capture capture,int texture,Matrix4f projection) {
        draw(capture,texture,projection,false);
    }
    static void draw(Capture capture,int texture,Matrix4f projection,boolean cull) {
        var data=BufferUtils.createFloatBuffer(capture.vertices.size()/4*6*9);
        for(int start=0;start<capture.vertices.size();start+=4)for(int index:new int[]{0,1,2,0,2,3})data.put(capture.vertices.get(start+index));
        data.flip();GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,36,0);
        GL20.glEnableVertexAttribArray(1);GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,36,12);
        GL20.glEnableVertexAttribArray(2);GL20.glVertexAttribPointer(2,4,GL11.GL_FLOAT,false,36,20);
        GL30.glVertexAttribI2i(3,0,0);GL30.glVertexAttribI2i(4,0,0);GL20.glVertexAttrib3f(5,0,0,1);
        var matrix=BufferUtils.createFloatBuffer(16);projection.get(matrix);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ProjMat"),false,matrix);
        new Matrix4f().get(matrix);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ModelViewMat"),false,matrix);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Sampler0"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Sampler1"),1);
        GL20.glUniform4f(GL20.glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
        GL20.glUniform3f(GL20.glGetUniformLocation(program,"Light0_Direction"),0,0,1);
        GL20.glUniform3f(GL20.glGetUniformLocation(program,"Light1_Direction"),0,0,1);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"FogStart"),1000);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"FogEnd"),100000);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,data.remaining()/9);
    }
    static BufferedImage pixels() {
        var buffer=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {int offset=(y*W+x)*4;
            image.setRGB(x,H-1-y,((buffer.get(offset)&255)<<16)|((buffer.get(offset+1)&255)<<8)|(buffer.get(offset+2)&255));}
        return image;
    }
    private static GunType view() {
        var ak=(GunType)InfoType.infoTypes.stream().filter(t->t instanceof GunType&&"AK74M".equals(t.shortName)).findFirst().orElseThrow();
        var tree=new ArrayList<List<ModifiableType.DefaultAttachment>>();for(var group:ak.defaultAttachments)tree.add(new ArrayList<>(group));
        while(tree.size()<ak.slots.length)tree.add(new ArrayList<>());
        var part=new ModifiableType.DefaultAttachment();part.name="PSO1";part.slot=6;tree.get(6).add(part);return ak.withAttachments(tree);
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();var view=view();var scope=GunSightState.scopeSight(view);
        check(scope!=null&&scope.type().shortName.equals("PSO1"),"actual AK74M selects PSO-1");
        var type=scope.type();var rig=ModelCache.rigFor(type,type.modelScale);var glass=ModelCache.rigForReference(type.scopeGlassModel,1F);
        var body=ModelCache.load(view);var gunPose=new Matrix4f().translate(.56F,-.52F,-.72F);
        FmumFirstPersonPose.itemFrame(gunPose);FmumFirstPersonPose.placement(gunPose,body,1,0,0);
        var eye=FmumFirstPersonPose.eyeInGunSpace(new Matrix4f(gunPose));FmumFirstPersonPose.aim(gunPose,view,1,eye);
        var toEye=gunPose.translate(ScopeReticleRenderer.mount(scope)).rotateX((float)Math.toRadians(scope.roll()-type.aimCenterX));
        var center=FmumSightPicture.vector(rig.animation,"pupilRefCenter",0,0,0).mul(type.modelScale);
        var scale=FmumSightPicture.vector(rig.animation,"scopeGlassModelScale",1,1,1).mul(type.modelScale);
        var pose=new PoseStack();pose.mulPose(new Matrix4f(toEye).translate(center).scale(scale));
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05F,100);
        var world=new Capture();FmumSightRenderer.renderLensSurface(pose,world,glass,new Matrix4f(projection).mul(pose.last().pose()),1,false);
        check(world.vertices.size()==glass.quads.stream().filter(q->q.nx()<-.9F).count()*4,"exactly the front aperture faces are submitted");
        var reticle=new Capture();ScopeReticleRenderer.render(pose,reticle,view,scope,rig,glass,toEye,projection,17.5,0,false);
        check(!reticle.vertices.isEmpty(),"real reticle emitted at real camera pose");
        var picture=ScopeEyeRelief.picture(rig.animation,type.modelScale,toEye,0);check(picture.visible(),"actual ADS eye box visible");
        var shadow=new Capture();ScopeMaskRenderer.render(pose,shadow,rig,type,glass,toEye,0,0,70,0,false);
        RenderSystem.initRenderThread();check(GLFW.glfwInit(),"GLFW initialized");GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"offline scope composition",0,0);check(window!=0,"invisible context");
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();initShader();
        var target=new TextureTarget(W,H,true,false);int sceneTexture=0,reticleTexture=0,whiteTexture=0;
        try {
            var scene=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<256;y++)for(int x=0;x<256;x++)scene.setRGB(x,y,(x%16<1||y%16<1)?0xFF789C9A:0xFFD2EEE0);
            sceneTexture=texture(scene);reticleTexture=texture(ImageIO.read(pack.path(Textures.forReticle(type,0,pack::has)).toFile()));
            var white=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);white.setRGB(0,0,0xFFFFFFFF);whiteTexture=texture(white);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(whiteTexture);RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            target.bindWrite(true);RenderSystem.clearColor(.1F,.15F,.2F,1);GL11.glClearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            final int sceneId=sceneTexture,reticleId=reticleTexture,whiteId=whiteTexture;
            ScopeRenderLayers.composite(()->draw(world,sceneId,projection));var background=pixels();
            ScopeRenderLayers.composite(()->draw(reticle,reticleId,projection));var withReticle=pixels();
            ScopeRenderLayers.composite(()->draw(shadow,reticleId,projection));var complete=pixels();
            Path output=Path.of(args[1]);Files.createDirectories(output);
            ImageIO.write(background,"png",output.resolve("world-only.png").toFile());
            ImageIO.write(withReticle,"png",output.resolve("reticle.png").toFile());
            ImageIO.write(complete,"png",output.resolve("composite.png").toFile());
            check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"depth state restored after all optical layers");
            var depth=BufferUtils.createFloatBuffer(1);GL11.glReadPixels(W/2,H/2,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            check(Math.abs(depth.get(0)-1F)<1E-5,"optical layers never overwrite scene depth");
            int inside=0,dark=0;
            for(int y=H/2-55;y<H/2+55;y++)for(int x=W/2-75;x<W/2+75;x++) {
                int base=background.getRGB(x,y),after=withReticle.getRGB(x,y);
                if(((base>>8)&255)<120)continue;inside++;
                if(((after>>16)&255)<50&&((after>>8)&255)<50&&(after&255)<50)dark++;
            }
            System.out.println("CENTRAL_PIXELS="+inside+" BLACK_RETICLE_PIXELS="+dark);
            check(inside>5000,"unobstructed pupil contains the world image");
            check(dark>15,"real PSO-1 divisions are visible");
            check(dark<inside*.20,"authored outer mask cannot collapse into an opaque centre square");
            int darkenedCenter=0;
            for(int y=H/2-25;y<H/2+25;y++)for(int x=W/2-25;x<W/2+25;x++)
                if(complete.getRGB(x,y)!=withReticle.getRGB(x,y))darkenedCenter++;
            check(darkenedCenter<10,"pupil rim interpolation cannot draw horizontal bands across the clear centre");
            check(complete.getRGB(5,5)==background.getRGB(5,5),"optical layers never touch the surrounding world");
            GL11.glClearDepth(.25);RenderSystem.depthMask(true);RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,false);
            ScopeRenderLayers.composite(()->draw(world,sceneId,projection));
            ScopeRenderLayers.composite(()->draw(reticle,reticleId,projection));
            ScopeRenderLayers.composite(()->draw(shadow,reticleId,projection));
            var occluded=pixels();int changed=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(occluded.getRGB(x,y)!=complete.getRGB(x,y))changed++;
            check(changed==0,"nearer housing depth hides world, reticle and mask together");
            try {ScopeRenderLayers.composite(()->{throw new IllegalStateException("deliberate GPU cleanup fixture");});}
            catch(IllegalStateException expected) {check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"depth state restored after failure");}
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"no GPU error");
        } finally {
            target.destroyBuffers();GL11.glDeleteTextures(sceneTexture);GL11.glDeleteTextures(reticleTexture);GL11.glDeleteTextures(whiteTexture);
            GL15.glDeleteBuffers(vbo);GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
        System.out.println("VerifyScopeComposition: "+checks+" checks passed; actual AK74M/PSO-1, original atlas, ordered GPU pixels and depth preservation. Synthetic scene; no game.");
    }
}
