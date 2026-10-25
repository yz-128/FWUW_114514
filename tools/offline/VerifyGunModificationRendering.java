import com.flansmod.client.gui.GunModificationPreview;
import com.flansmod.client.gui.GunModificationView;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.Textures;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.objectweb.asm.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual preview vertex method, actual RenderType GL state shards, original pack textures,
 * and Minecraft's emissive shader in an invisible framebuffer. Resource binding is supplied
 * offline: this does not instantiate Minecraft, GuiGraphics, a world or a shader pack.
 * Args: output EFT-pack [--diagnose]. Old JARs use their original vanilla RenderType.
 */
public final class VerifyGunModificationRendering {
    static final int W=VerifyScopeComposition.W,H=VerifyScopeComposition.H;
    static int checks,cases,white;static Path output;static OfflinePack pack;
    static Method emission,layerFactory,depthGuard;static boolean currentFireMode,positionColourShader;
    static Vector3f light0=new Vector3f(),light1=new Vector3f(),normal=new Vector3f(0,0,1);
    static final Map<ResourceLocation,Integer> textures=new HashMap<>();
    static final List<String> findings=new ArrayList<>();
    record Piece(InfoType type,BakedRig rig,Matrix4f transform){}
    record Draw(VerifyScopeComposition.Capture mesh,int texture,RenderType type){}
    record Call(String owner,String name,String descriptor){}
    static void check(boolean condition,String name){checks++;if(!condition){findings.add(name);System.out.println("FAIL "+name);}}
    static Object field(Object owner,String name)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(owner.getClass()+"."+name);
    }
    static RenderType layer(ResourceLocation texture)throws Exception{return layerFactory==null?RenderType.entityTranslucentEmissive(texture):(RenderType)layerFactory.invoke(null,texture);}
    /** Follow only methods reachable from the final JAR's Preview.render, including lambda bodies.
     * Merely finding the helper class is insufficient: the classic JAR historically included it
     * while its replacement Preview still called vanilla's colour-only layer. */
    static void productionPath()throws Exception{
        String owner=GunModificationPreview.class.getName().replace('.','/');var methods=new LinkedHashMap<Call,List<Call>>();
        var location=GunModificationPreview.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Production preview origin="+location);
        check(location.getPath().endsWith(".jar"),"production preview is loaded from the immutable final JAR");
        try(var stream=GunModificationPreview.class.getResourceAsStream("GunModificationPreview.class")){
            if(stream==null)throw new IllegalStateException("Missing final preview bytecode");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    var calls=new ArrayList<Call>();methods.put(new Call(owner,name,descriptor),calls);
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String calledOwner,String calledName,String calledDescriptor,boolean itf){calls.add(new Call(calledOwner,calledName,calledDescriptor));}
                        @Override public void visitInvokeDynamicInsn(String calledName,String calledDescriptor,Handle bootstrap,Object... arguments){for(Object argument:arguments)if(argument instanceof Handle handle&&owner.equals(handle.getOwner()))calls.add(new Call(owner,handle.getName(),handle.getDesc()));}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        var pending=new ArrayDeque<Call>();methods.keySet().stream().filter(m->m.name.equals("render")).forEach(pending::add);
        var reachable=new LinkedHashSet<Call>();while(!pending.isEmpty()){var call=pending.removeFirst();if(reachable.add(call))pending.addAll(methods.getOrDefault(call,List.of()));}
        boolean helper=reachable.stream().anyMatch(c->c.owner.equals("com/flansmod/client/gui/GunModificationRenderLayers")&&c.name.equals("mesh"));
        boolean vanilla=reachable.stream().anyMatch(c->c.owner.equals("net/minecraft/client/renderer/RenderType")&&c.name.equals("entityTranslucentEmissive"));
        boolean guarded=reachable.stream().anyMatch(c->c.owner.equals("com/flansmod/client/gui/GunModificationRenderLayers")&&c.name.equals("withDepth"));
        check(helper!=vanilla,"one unambiguous render layer is reachable from the production preview");
        if(helper){var type=Class.forName("com.flansmod.client.gui.GunModificationRenderLayers");layerFactory=type.getMethod("mesh",ResourceLocation.class);if(guarded)depthGuard=type.getMethod("withDepth",Runnable.class);}
        currentFireMode=reachable.stream().anyMatch(c->c.owner.equals(owner)&&c.name.equals("renderMesh")&&c.descriptor.endsWith("ZI)V"));
        emission=currentFireMode?GunModificationPreview.class.getDeclaredMethod("renderMesh",PoseStack.class,VertexConsumer.class,BakedRig.class,boolean.class,int.class):GunModificationPreview.class.getDeclaredMethod("renderMesh",PoseStack.class,VertexConsumer.class,BakedRig.class,boolean.class);
        emission.setAccessible(true);
        System.out.printf("Actual render call graph: layer=%s guard=%s fireModeSlice=%s%n",helper?"GunModificationRenderLayers.mesh":"RenderType.entityTranslucentEmissive",guarded,currentFireMode);
        check(helper&&guarded,"production Preview actually calls the depth-writing layer and scoped guard");
        check(currentFireMode,"production Preview actually renders the current fire-mode slice");
    }
    static List<RenderStateShard> state(RenderType type)throws Exception{
        var state=field(type,"state");var shards=new ArrayList<RenderStateShard>();
        for(String name:List.of("transparencyState","depthTestState","cullState","writeMaskState"))shards.add((RenderStateShard)field(state,name));
        return shards;
    }
    static String shaderSource(String path)throws Exception{
        try(var stream=VerifyGunModificationRendering.class.getClassLoader().getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing official Minecraft shader "+path);
            String source=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var matcher=java.util.regex.Pattern.compile("#moj_import <([^>]+)>").matcher(source);var expanded=new StringBuffer();
            while(matcher.find())matcher.appendReplacement(expanded,java.util.regex.Matcher.quoteReplacement(shaderSource("assets/minecraft/shaders/include/"+matcher.group(1))));
            matcher.appendTail(expanded);return path.contains("/include/")?expanded.toString().replaceAll("(?m)^\\s*#version[^\\r\\n]*", ""):expanded.toString();
        }
    }
    static int shader(int kind,String source){int shader=GL20.glCreateShader(kind);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));return shader;}
    static void initProductionShader()throws Exception{
        var format=layer(ResourceLocation.fromNamespaceAndPath("flansmod","offline/shader-contract.png")).format();
        positionColourShader=format==DefaultVertexFormat.POSITION_TEX_COLOR;
        check(positionColourShader||format==DefaultVertexFormat.NEW_ENTITY,"production layer uses an explicitly supported vertex/shader contract");
        if(!positionColourShader){VerifyScopeComposition.initShader();System.out.println("Shader: official rendertype_entity_translucent_emissive / NEW_ENTITY");return;}
        // Verify the actual shared layer references this shader; the test does not silently replace
        // an arbitrary custom shader just because its vertex format happens to match.
        boolean[] references={false};
        try(var stream=Class.forName("com.flansmod.client.gui.GunModificationRenderLayers").getResourceAsStream("GunModificationRenderLayers.class")){
            new ClassReader(Objects.requireNonNull(stream)).accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){@Override public void visitInvokeDynamicInsn(String name,String descriptor,Handle bootstrap,Object... arguments){for(Object argument:arguments)if(argument instanceof Handle h&&h.getOwner().equals("net/minecraft/client/renderer/GameRenderer")&&h.getName().equals("getPositionTexColorShader"))references[0]=true;}};}},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        check(references[0],"actual production layer selects official GameRenderer.position_tex_color shader");
        int vertex=shader(GL20.GL_VERTEX_SHADER,shaderSource("assets/minecraft/shaders/core/position_tex_color.vsh"));
        int fragment=shader(GL20.GL_FRAGMENT_SHADER,shaderSource("assets/minecraft/shaders/core/position_tex_color.fsh"));
        VerifyScopeComposition.program=GL20.glCreateProgram();GL20.glAttachShader(VerifyScopeComposition.program,vertex);GL20.glAttachShader(VerifyScopeComposition.program,fragment);
        GL20.glBindAttribLocation(VerifyScopeComposition.program,0,"Position");GL20.glBindAttribLocation(VerifyScopeComposition.program,1,"UV0");GL20.glBindAttribLocation(VerifyScopeComposition.program,2,"Color");GL20.glLinkProgram(VerifyScopeComposition.program);
        if(GL20.glGetProgrami(VerifyScopeComposition.program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(VerifyScopeComposition.program));
        GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);VerifyScopeComposition.vao=GL30.glGenVertexArrays();VerifyScopeComposition.vbo=GL15.glGenBuffers();
        System.out.println("Shader: official position_tex_color / POSITION_TEX_COLOR");
    }
    static void collect(List<Piece> pieces,ModifiableType owner,List<ModifiableType.DefaultAttachment> nodes,Matrix4f parent){
        for(var node:nodes){if(node.slot<0||node.slot>=owner.slots.length)continue;var type=AttachmentType.getAttachment(node.name);if(type==null)continue;
            var slot=owner.slots[node.slot];var pose=new Matrix4f(parent).translate(slot.x+node.pos,slot.y,slot.z).rotateX((float)Math.toRadians(slot.rotate));
            var rig=ModelCache.rigFor(type,type.modelScale);if(rig!=null&&!rig.quads.isEmpty())pieces.add(new Piece(type,rig,pose));collect(pieces,type,node.children,pose);}
    }
    static int texture(InfoType type)throws Exception{
        var id=Textures.forType(type,pack::has);var cached=textures.get(id);if(cached!=null)return cached;
        var path=pack.path(id);if(path==null)throw new IllegalStateException("Missing original texture "+id);
        int gl=VerifyScopeComposition.texture(ImageIO.read(path.toFile()));textures.put(id,gl);return gl;
    }
    static VerifyScopeComposition.Capture sort(VerifyScopeComposition.Capture input,RenderType type)throws Exception{
        if(!(boolean)field(type,"sortOnUpload"))return input;
        int count=input.vertices.size()/4;var centres=new Vector3f[count];for(int q=0;q<count;q++){var p=new Vector3f();for(int j=0;j<4;j++){var v=input.vertices.get(q*4+j);p.add(v[0],v[1],v[2]);}centres[q]=p.mul(.25F);}
        var result=new VerifyScopeComposition.Capture();for(int q:VertexSorting.ORTHOGRAPHIC_Z.sort(centres))for(int j=0;j<4;j++)result.vertices.add(input.vertices.get(q*4+j));return result;
    }
    static void draw(VerifyScopeComposition.Capture capture,int texture,Matrix4f projection){
        var data=BufferUtils.createFloatBuffer(capture.vertices.size()/4*6*9);for(int start=0;start<capture.vertices.size();start+=4)for(int i:new int[]{0,1,2,0,2,3})data.put(capture.vertices.get(start+i));data.flip();
        int program=VerifyScopeComposition.program;GL20.glUseProgram(program);GL30.glBindVertexArray(VerifyScopeComposition.vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,VerifyScopeComposition.vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,36,0);GL20.glEnableVertexAttribArray(1);GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,36,12);GL20.glEnableVertexAttribArray(2);GL20.glVertexAttribPointer(2,4,GL11.GL_FLOAT,false,36,20);
        GL30.glVertexAttribI2i(3,0,0);GL30.glVertexAttribI2i(4,0,0);GL20.glVertexAttrib3f(5,normal.x,normal.y,normal.z);
        var matrix=BufferUtils.createFloatBuffer(16);projection.get(matrix);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ProjMat"),false,matrix);new Matrix4f().get(matrix);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ModelViewMat"),false,matrix);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Sampler0"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Sampler1"),1);GL20.glUniform4f(GL20.glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
        GL20.glUniform3f(GL20.glGetUniformLocation(program,"Light0_Direction"),light0.x,light0.y,light0.z);GL20.glUniform3f(GL20.glGetUniformLocation(program,"Light1_Direction"),light1.x,light1.y,light1.z);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"FogStart"),1000);GL20.glUniform1f(GL20.glGetUniformLocation(program,"FogEnd"),100000);RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,data.remaining()/9);
    }
    static void clear(){RenderSystem.depthMask(true);RenderSystem.clearColor(.06F,.10F,.14F,1);GL11.glClearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();}
    static BufferedImage render(List<Draw> draws,Matrix4f projection,boolean reference)throws Exception{
        clear();for(var item:draws){
            if(reference){RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();draw(item.mesh,item.texture,projection);}
            else {var shards=state(item.type);try{for(var shard:shards)shard.setupRenderState();draw(item.mesh,item.texture,projection);}finally{for(var shard:shards)shard.clearRenderState();}}
        }return VerifyScopeComposition.pixels();
    }
    static int difference(BufferedImage a,BufferedImage b){int count=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int c=a.getRGB(x,y),d=b.getRGB(x,y);if(Math.abs(((c>>16)&255)-((d>>16)&255))+Math.abs(((c>>8)&255)-((d>>8)&255))+Math.abs((c&255)-(d&255))>30)count++;}return count;}
    static int depthPixels(){var data=BufferUtils.createFloatBuffer(W*H);GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);int count=0;for(int i=0;i<W*H;i++)if(data.get(i)<.999999F)count++;return count;}
    static void sample(String name,float yaw,float pitch)throws Exception{
        var gun=pack.types.stream().filter(t->t instanceof GunType&&name.equals(t.shortName)).map(t->(GunType)t).findFirst().orElseThrow();
        var receiver=ModelCache.rigFor(gun,gun.modelScale);var pieces=new ArrayList<Piece>();pieces.add(new Piece(gun,receiver,new Matrix4f()));collect(pieces,gun,gun.defaultAttachments.stream().flatMap(List::stream).toList(),new Matrix4f());
        var bounds=new ArrayList<Vector3f>();for(var piece:pieces)for(int i=0;i<8;i++)bounds.add(piece.transform.transformPosition(new Vector3f((i&1)==0?piece.rig.minX:piece.rig.maxX,(i&2)==0?piece.rig.minY:piece.rig.maxY,(i&4)==0?piece.rig.minZ:piece.rig.maxZ)));
        var frame=GunModificationView.frame(bounds,yaw,pitch,1,32,32,W-64,H-64);var draws=new ArrayList<Draw>();int vertices=0;float shadeMin=1;
        int mode=gun.fireModeIndex(ItemStack.EMPTY);
        for(var piece:pieces){var pose=new PoseStack();pose.mulPose(frame.matrix());pose.mulPose(piece.transform);var capture=new VerifyScopeComposition.Capture();if(currentFireMode)emission.invoke(null,pose,capture,piece.rig,false,mode);else emission.invoke(null,pose,capture,piece.rig,false);var type=layer(Textures.forType(piece.type));draws.add(new Draw(sort(capture,type),texture(piece.type),type));vertices+=capture.vertices.size();for(var v:capture.vertices)shadeMin=Math.min(shadeMin,Math.min(v[5],Math.min(v[6],v[7])));}
        var projection=new Matrix4f().ortho(0,W,H,0,1000,21000).translate(0,0,-11000);RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);
        String label=name+"-yaw"+String.format(Locale.ROOT,"%+.0f",yaw)+"-pitch"+String.format(Locale.ROOT,"%+.0f",pitch);
        var nativeImage=render(draws,projection,true);ImageIO.write(nativeImage,"png",output.resolve(label+"-depth-reference.png").toFile());int nativeDepth=depthPixels();
        var production=render(draws,projection,false);ImageIO.write(production,"png",output.resolve(label+"-production.png").toFile());int productionDepth=depthPixels();
        var reversed=new ArrayList<>(draws);Collections.reverse(reversed);var reordered=render(reversed,projection,false);ImageIO.write(reordered,"png",output.resolve(label+"-reversed-pieces.png").toFile());
        int wrong=difference(nativeImage,production),unstable=difference(production,reordered);
        System.out.printf(Locale.ROOT,"%s pieces=%d textures=%d vertices=%d studioMin=%.4f depth=%d/%d referenceDifference=%d pieceOrderDifference=%d sortOnUpload=%s%n",label,pieces.size(),draws.stream().map(Draw::texture).distinct().count(),vertices,shadeMin,productionDepth,nativeDepth,wrong,unstable,field(draws.getFirst().type,"sortOnUpload"));
        check(pieces.size()>4&&draws.stream().map(Draw::texture).distinct().count()>1,"actual cross-texture assembly "+label);
        check(shadeMin>=.69F,"real preview studio shading remains readable with zero world lights "+label);
        check(nativeDepth>3000,"independent real assembly fixture writes substantial depth "+label);
        check(productionDepth>nativeDepth*.95,"production RenderType writes the real opaque assembly depth "+label);
        check(wrong<=Math.max(20,nativeDepth*.002),"production pixels agree with original geometry depth reference "+label);
        check(unstable<=Math.max(30,nativeDepth*.004),"cross-texture piece order does not reveal hidden rear surfaces "+label);
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"GL error "+label);cases++;
    }
    static VerifyScopeComposition.Capture quad(float left,float top,float right,float bottom,float z,float r,float g,float b){
        var capture=new VerifyScopeComposition.Capture();for(float[] p:new float[][]{{left,top},{right,top},{right,bottom},{left,bottom}})capture.vertices.add(new float[]{p[0],p[1],z,.5F,.5F,r,g,b,1});return capture;
    }
    static Matrix4f guiProjection(){return new Matrix4f().ortho(0,W,H,0,1000,21000).translate(0,0,-11000);}
    static float depthAt(int x,int y){var value=BufferUtils.createFloatBuffer(1);GL11.glReadPixels(x,y,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,value);return value.get(0);}
    static void vertexFormatContract()throws Exception{
        var gun=pack.types.stream().filter(t->t instanceof GunType&&"AKS74U".equals(t.shortName)).map(t->(GunType)t).findFirst().orElseThrow();
        var rig=ModelCache.rigFor(gun,gun.modelScale);var type=layer(Textures.forType(gun));int mode=gun.fireModeIndex(ItemStack.EMPTY);
        var pose=new PoseStack();pose.mulPose(new Matrix4f().scale(120,-120,120));var captured=new VerifyScopeComposition.Capture();
        if(currentFireMode)emission.invoke(null,pose,captured,rig,false,mode);else emission.invoke(null,pose,captured,rig,false);
        // A recorder accepts every VertexConsumer setter. Real BufferBuilder additionally checks
        // that every required attribute is written and safely ignores setters absent from format.
        try(var bytes=new ByteBufferBuilder(1<<20)){
            var builder=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,type.format());
            if(currentFireMode)emission.invoke(null,pose,builder,rig,false,mode);else emission.invoke(null,pose,builder,rig,false);
            try(var mesh=builder.buildOrThrow()){
                check(mesh.drawState().vertexCount()==captured.vertices.size(),"real BufferBuilder accepts all preview setters and preserves actual vertex count");
                check(mesh.drawState().format()==type.format(),"real MeshData uses the actual production RenderType vertex format");
            }
        }
    }
    static void studioLightContract()throws Exception{
        var type=layer(ResourceLocation.fromNamespaceAndPath("flansmod","offline/studio.png"));
        var draws=List.of(new Draw(quad(40,40,160,140,100,.7F,.7F,.7F),white,type));BufferedImage baseline=null;
        for(int i=0;i<3;i++){
            if(i==0){light0.set(0);light1.set(0);normal.set(0,0,1);}
            if(i==1){light0.set(0,0,1);light1.set(0,0,1);normal.set(0,0,1);}
            if(i==2){light0.set(0,1,0);light1.set(1,0,0);normal.set(0,0,-1);}
            var actual=render(draws,guiProjection(),false);int colour=actual.getRGB(80,80),red=(colour>>16)&255;
            check(red>=176&&red<=181,"studio vertex colour survives without a second world-light multiplier case="+i);
            if(baseline==null)baseline=actual;else check(difference(baseline,actual)==0,"GUI preview is independent of world lights and mesh normals case="+i);
            System.out.printf("studio-light-case=%d centre=%06X%n",i,colour&0xFFFFFF);
        }
        light0.set(0);light1.set(0);normal.set(0,0,1);
    }
    static void alphaAndCache()throws Exception{
        var id=ResourceLocation.fromNamespaceAndPath("flansmod","offline/alpha.png");var first=layer(id);
        check(first==layer(ResourceLocation.parse(id.toString())),"equal texture identifiers reuse the identical production RenderType");
        check(first!=layer(ResourceLocation.fromNamespaceAndPath("flansmod","offline/other.png")),"different texture identifiers retain separate production layers");
        check(!(boolean)field(first,"sortOnUpload"),"preview preserves native face order rather than sorting translucent quads");
        for(int alpha:new int[]{0,12,64,128,255}){
            var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,(alpha<<24)|0xFF4020);int texture=VerifyScopeComposition.texture(image);
            try{
                var draws=List.of(new Draw(quad(40,40,160,140,100,1,1,1),texture,first));
                var reference=render(draws,guiProjection(),true);var actual=render(draws,guiProjection(),false);int difference=difference(reference,actual);
                check(difference==0,"FMUM alpha blending with the shared Minecraft alpha cutoff alpha="+alpha);
                check((alpha>=26)==(depthAt(80,H-80)<1),"alpha discard alone decides depth writes alpha="+alpha);
                ImageIO.write(actual,"png",output.resolve("alpha-"+alpha+"-production.png").toFile());
                if(alpha==128)ImageIO.write(reference,"png",output.resolve("alpha-128-native-reference.png").toFile());
                System.out.printf("alpha=%d referenceDifference=%d centre=%06X/%06X%n",alpha,difference,actual.getRGB(80,80)&0xFFFFFF,reference.getRGB(80,80)&0xFFFFFF);
            }finally{com.mojang.blaze3d.platform.GlStateManager._deleteTexture(texture);}
        }
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"alpha/cache contract GL error");
    }
    static void guard()throws Exception{
        if(depthGuard==null){check(false,"production includes a scoped preview depth guard");return;}
        RenderSystem.disableScissor();RenderSystem.depthMask(true);GL11.glClearDepth(.375);RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,false);
        RenderSystem.enableScissor(20,30,100,80);RenderSystem.depthMask(true);GL11.glClearDepth(.25);RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,false);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.depthFunc(GL11.GL_GREATER);RenderSystem.enableCull();GL11.glClearDepth(.625);
        int[] drawnColour={0};int outsideColour=VerifyScopeComposition.pixels().getRGB(2,H-1-2);
        depthGuard.invoke(null,(Runnable)()->{
            check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_LEQUAL&&!GL11.glIsEnabled(GL11.GL_CULL_FACE),"real preview guard enables opaque depth and two-sided geometry");
            check(depthAt(40,40)==1,"real guard clears stale GUI depth in current scissor");
            check(Math.abs(depthAt(2,2)-.375)<1E-6,"entry clear preserves depth outside the current scissor");
            draw(quad(20,H-110,120,H-30,740,1,1,1),white,guiProjection());
            check(depthAt(40,40)<.5,"zoomed model fixture writes depth closer than GUI z500 labels");
            drawnColour[0]=VerifyScopeComposition.pixels().getRGB(40,H-1-40);
        });
        check(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_GREATER&&GL11.glIsEnabled(GL11.GL_CULL_FACE),"preview depth guard restores caller state");
        check(Math.abs(GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE)-.625)<1E-8,"preview guard restores depth clear value");
        check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"preview guard leaves caller scissor enabled");
        var box=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);check(box.get(0)==20&&box.get(1)==30&&box.get(2)==100&&box.get(3)==80,"preview guard retains caller scissor rectangle");
        check(depthAt(40,40)==1,"exit clear removes preview depth before subsequent GUI overlays");
        check(Math.abs(depthAt(2,2)-.375)<1E-6,"exit clear preserves depth outside the current scissor");
        var colours=VerifyScopeComposition.pixels();check(colours.getRGB(40,H-1-40)==drawnColour[0],"exit clear preserves the rendered model colour buffer");
        check(colours.getRGB(2,H-1-2)==outsideColour,"preview guard preserves colour outside current scissor");
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();
        draw(quad(20,H-110,120,H-30,500,1,0,0),white,guiProjection());
        int after=VerifyScopeComposition.pixels().getRGB(40,H-1-40)&0xFFFFFF;check(((after>>16)&255)>30&&(after&0xFFFF)==0,"subsequent GUI z500 label remains visible above a zoomed z740 model");
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.depthFunc(GL11.GL_GREATER);RenderSystem.enableCull();
        boolean exceptionPropagated=false;
        try{depthGuard.invoke(null,(Runnable)()->{draw(quad(20,H-110,120,H-30,740,1,1,1),white,guiProjection());throw new IllegalStateException("deliberate preview cleanup fixture");});}catch(InvocationTargetException expected){exceptionPropagated=expected.getCause() instanceof IllegalStateException;}
        check(exceptionPropagated,"guard preserves original exception");
        check(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_GREATER&&GL11.glIsEnabled(GL11.GL_CULL_FACE),"preview depth guard restores state on failure");
        check(depthAt(40,40)==1,"failure cleanup also removes scoped preview depth");
        check(Math.abs(depthAt(2,2)-.375)<1E-6,"failure cleanup preserves depth outside current scissor");
        check(Math.abs(GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE)-.625)<1E-8,"failure cleanup restores caller depth clear value");
        RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();output=Path.of(args[0]);Files.createDirectories(output);pack=new OfflinePack(Path.of(args[1]));pack.readTypes();boolean diagnose=Arrays.asList(args).contains("--diagnose");
        productionPath();
        RenderSystem.initRenderThread();if(!GLFW.glfwInit())throw new IllegalStateException("GLFW");GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);long window=GLFW.glfwCreateWindow(W,H,"actual gun modification mesh",0,0);GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();initProductionShader();var target=new TextureTarget(W,H,true,false);
        try{var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xFFFFFFFF);white=VerifyScopeComposition.texture(image);RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(white);RenderSystem.activeTexture(GL13.GL_TEXTURE0);target.bindWrite(true);
            vertexFormatContract();for(String name:List.of("AKS74U","AK74M"))for(float[] angle:new float[][]{{-24,0},{24,15},{155,-15}})sample(name,angle[0],angle[1]);alphaAndCache();studioLightContract();guard();
        }finally{target.destroyBuffers();for(int texture:textures.values())com.mojang.blaze3d.platform.GlStateManager._deleteTexture(texture);com.mojang.blaze3d.platform.GlStateManager._deleteTexture(white);GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        Files.write(output.resolve("findings.txt"),findings);System.out.println("VerifyGunModificationRendering: "+checks+" checks, "+cases+" actual assembly GPU cases, "+findings.size()+" findings; production GUI vertices/RenderType GL shards, original textures and vanilla shader; no game.");if(!diagnose&&!findings.isEmpty())throw new AssertionError(findings.size()+" preview GPU findings");
    }
}
