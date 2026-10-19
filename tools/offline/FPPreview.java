import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.flansmod.client.model.loader.*;
import com.flansmod.common.types.*;
import com.flansmod.client.render.FmumFirstPersonPose;
import com.flansmod.client.render.LegacySightPose;
import com.flansmod.client.render.FmumActionPose;
import com.flansmod.client.render.LegacyGunMotion;
import com.flansmod.client.render.FmumReloadPose;
import com.flansmod.client.render.FmumBulletPose;
import com.flansmod.common.GunActionState;
import com.flansmod.common.GunReloadState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * First-person offline preview: renders the gun + attachments + arms through exactly the
 * renderer's transform chain, from the player's eye, so arm placement can be judged without
 * launching the game.
 *
 * <p>Usage: FPPreview <packDir> <GunShortName> <out.png> [armRx] [armRy] [armRz] [extraRy] [extraRx] [extraRz]
 * where armRx..armRz override the class's stored leftArmRot (degrees, blank = use stored) and
 * extra* are an additional rotation applied inside the arm chain before the scale.
 */
public class FPPreview {
    static final int W = 854, H = 480;
    static final float TAN_HALF = (float) Math.tan(Math.toRadians(Double.parseDouble(System.getProperty("fp.fov","70")) / 2));
    static final float ASPECT = (float) W / H;
    static int[] owners;
    static final float NEAR = 0.05F;
    static GunActionState.Frame ACTION=GunActionState.Frame.IDLE;
    static GunReloadState.Frame RELOAD=GunReloadState.Frame.IDLE;
    static GunType previewGun;

    record Part(Matrix4f m, BakedRig rig, BufferedImage tex, int tint,float alpha,boolean glowing,boolean effect) {
        Part(Matrix4f m,BakedRig rig,BufferedImage tex,int tint){this(m,rig,tex,tint,1F,false,false);}
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        String wanted = args[1];
        File out = new File(args[2]);
        String[] ar = new String[] { "", "", "" };
        for (int i = 0; i < 3 && args.length > 3 + i; i++) ar[i] = args[3 + i];
        float extraRx = args.length > 6 ? Float.parseFloat(args[6]) : 0F;
        float extraRy = args.length > 7 ? Float.parseFloat(args[7]) : 0F;
        float extraRz = args.length > 8 ? Float.parseFloat(args[8]) : 0F;

        // Do not walk TypeFile's HashMap: guns can then be read before attachment names exist,
        // so the real parser drops their default attachment trees and the preview loses sights.
        OfflinePack pack = new OfflinePack(root);
        pack.readTypes();
        GunType gun = (GunType)pack.types.stream().filter(t -> t instanceof GunType
                && t.shortName != null && t.shortName.equalsIgnoreCase(wanted)).findFirst().orElse(null);
        if (gun == null) { System.out.println("gun not found"); return; }
        previewGun=gun;

        List<Part> parts = new ArrayList<>();
        BakedRig body = ModelCache.rigFor(gun, gun.modelScale);
        if (body == null) { System.out.println("no body"); return; }

        // Shared with the live renderer. State overrides never require desktop/game input.
        float ads = Float.parseFloat(System.getProperty("fp.ads", "0"));
        float sprint = Float.parseFloat(System.getProperty("fp.sprint", "0"));
        float reload = Float.parseFloat(System.getProperty("fp.reload", "0"));
        var step=GunReloadState.Step.valueOf(System.getProperty("fp.reloadStep","NONE"));
        float stepProgress=Float.parseFloat(System.getProperty("fp.reloadProgress","0"));
        if(step!=GunReloadState.Step.NONE) {
            RELOAD=new GunReloadState.Frame(step,stepProgress,step==GunReloadState.Step.TILT || step==GunReloadState.Step.UNTILT?stepProgress:step==GunReloadState.Step.LOAD_AMMO?0F:1F);
            reload=RELOAD.tilt();
        }
        float recoil = Float.parseFloat(System.getProperty("fp.recoil", "0"));
        ACTION=new GunActionState.Frame(GunActionState.Operation.valueOf(System.getProperty("fp.operation","NONE")),
                Float.parseFloat(System.getProperty("fp.progress","0")),Float.parseFloat(System.getProperty("fp.takeOut","0")));
        ads=FmumActionPose.ads(body.animation,ACTION,ads);
        LegacyGunMotion.Frame motion=LegacyGunMotion.Frame.ZERO;
        if(Boolean.getBoolean("fp.motion")) {
            LegacyGunMotion state=new LegacyGunMotion();
            state.tick(body.animation,Float.parseFloat(System.getProperty("fp.yawDelta","10")),
                    Float.parseFloat(System.getProperty("fp.pitchDelta","-5")),Float.parseFloat(System.getProperty("fp.velocityDelta","-.25")),
                    Float.parseFloat(System.getProperty("fp.walked",".25")),Float.parseFloat(System.getProperty("fp.bob",".03")),ads>0);
            motion=state.frame(body.animation,1,ads,sprint);
        }
        LegacySightPose.Center sight = Boolean.getBoolean("fp.gunBodyAim")
                ? new LegacySightPose.Center(gun.shortName,gun.aimCenterX,0F,gun.aimCenterY,gun.aimCenterZ)
                : LegacySightPose.forGun(gun);
        Matrix4f chain = new Matrix4f().translate(0.56f, -0.52f, -0.72f);
        if(Boolean.getBoolean("fp.fmu2")) {
            chain.rotateY((float)Math.toRadians(90)).translate(-0.25f, -0.05f, -0.155f);
        } else {
            FmumFirstPersonPose.itemFrame(chain);
            var recoilState=new com.flansmod.client.model.FmumRecoil();
            var stats=com.flansmod.common.GunStats.effective(gun);
            for(int i=0;i<(int)recoil;i++) {
                recoilState.shoot(new com.flansmod.common.GunStats.Shot(gun,stats,stats.pitch(),0F,false),body.animation);
                recoilState.tick();
            }
            var kick=recoilState.frame(0F,null);
            FmumFirstPersonPose.placement(chain, body.animation, ads, sprint, reload,ACTION,motion,kick);
            var basis=FmumFirstPersonPose.rotation(body.animation,ads,sprint,0F,ACTION)
                    .mul(FmumFirstPersonPose.recoilRotation(body.animation,reload,kick));
            FmumActionPose.follow(chain,basis,motion);
            if(FmumFirstPersonPose.hasPose(body.animation)) FmumFirstPersonPose.aim(chain, sight, ads);
        }
        System.out.printf("pose=%s ads=%.2f sprint=%.2f reload=%.2f recoil=%.2f aim=(%.3f %.3f %.3f)%n",
                Boolean.getBoolean("fp.fmu2") ? "FMU2" : "FMUM1.1", ads, sprint, reload, recoil,
                gun.aimCenterX, gun.aimCenterY, gun.aimCenterZ);
        System.out.println("selected sight=" + sight);

        for(var entry:body.quadsByPart.entrySet()) {
            var subset=new BakedRig();subset.quads.addAll(entry.getValue());subset.animation=body.animation;
            var m=new Matrix4f(chain);
            switch(entry.getKey()) {
                case "slideModel" -> m.translate(FmumReloadPose.slide(body.animation,RELOAD,ACTION,0,false)*gun.modelScale,0,0);
                case "pumpModel" -> m.translate(FmumReloadPose.charge(body.animation,RELOAD,ACTION).handle()*gun.modelScale,0,0);
                case "pumpModel2" -> FmumActionPose.pump2(m,body.animation,FmumReloadPose.charge(body.animation,RELOAD,ACTION),gun.modelScale,true);
            }
            parts.add(new Part(m,subset,skin(root,gun.texture),0xFFFFFFFF));
        }
        for (List<ModifiableType.DefaultAttachment> group : gun.defaultAttachments)
            addNodes(parts, root, gun, group, new Matrix4f(chain), 0);
        String magName=System.getProperty("fp.mag");
        if(magName!=null) {
            GunType mag=InfoType.infoTypes.stream().filter(t -> t instanceof GunType && magName.equalsIgnoreCase(t.shortName)).map(t -> (GunType)t).findFirst().orElse(null);
            if(mag==null)throw new IllegalArgumentException("Unknown magazine "+magName);
            var rig=ModelCache.rigFor(mag,mag.modelScale);
            var magPose=FmumReloadPose.magazine(new Matrix4f(chain),body.animation,RELOAD,ACTION);
            parts.add(new Part(magPose,rig,skin(root,mag.texture),0xFFFFFFFF));
            addSeatedBullets(parts,root,mag,magPose,Integer.parseInt(System.getProperty("fp.rounds","30")));
        }
        if(gun.itsType==GunType.GunItsType.MAG) addSeatedBullets(parts,root,gun,chain,Integer.parseInt(System.getProperty("fp.rounds","30")));

        ModelGeometry.Model model = body.animation;
        if(Boolean.getBoolean("fp.armsOnly")) parts.clear();
        addArm(parts, root, model, gun.leftArmModel, gun.leftArmTexture, "leftArmPos", "leftArmRot", "leftArmScale",
                chain, ar, extraRx, extraRy, extraRz, 0xFF6FA8DC);
        addArm(parts, root, model, gun.rightArmModel, gun.rightArmTexture, "rightArmPos", "rightArmRot", "rightArmScale",
                chain, new String[] { "", "", "" }, 0F, 0F, 0F, 0xFFE06666);

        int effectsTick=Integer.parseInt(System.getProperty("fp.effectsTick","-1"));
        if(effectsTick>=0 && gun.itsType!=GunType.GunItsType.MAG) {
            var flash=com.flansmod.client.render.FmumMuzzlePose.flash(gun,model);
            var state=new com.flansmod.client.model.FmumShotEffects();
            state.shoot(gun,flash,Integer.parseInt(System.getProperty("fp.flashVariant","0")),37F,true);
            for(int i=0;i<effectsTick;i++)state.tick();
            if(flash!=null && flash.model()!=null && flash.texture()!=null && state.alpha(1)>=1F/255F) {
                var rig=ModelCache.rigForReference(flash.model(),1F);
                if(rig!=null) {
                    boolean shader=Boolean.getBoolean("fp.shaderEffects") && !Boolean.getBoolean("fp.legacyShaderEffects");
                    var effectPose=new Matrix4f(chain).mul(flash.pose(state.roll));
                    if(shader)effectPose.scale(com.flansmod.client.render.FmumShaderFlash.SIZE);
                    int tint=shader?0xFF000000|((int)(255*com.flansmod.client.render.FmumShaderFlash.RED)<<16)
                        |((int)(255*com.flansmod.client.render.FmumShaderFlash.GREEN)<<8)|(int)(255*com.flansmod.client.render.FmumShaderFlash.BLUE):0xFFFFFFFF;
                    parts.add(new Part(effectPose,rig,skin(root,flash.texture(state.variant)),tint,
                        state.alpha(1)*(shader?com.flansmod.client.render.FmumShaderFlash.OPACITY:1F),true,true));
                }
                System.out.printf("flash anchor=%s fade=%.4f alpha=%.4f origin=%s%n",flash.anchor(),flash.fade(),state.alpha(1),flash.mount().getTranslation(new Vector3f()));
            }
            for(boolean chamber:new boolean[]{false,true}) {
                var spec=chamber?gun.chamberSmoke:gun.muzzleSmoke;
                if(spec.model==null || spec.texture==null || spec.vanishConst<=0)continue;
                var origin=com.flansmod.client.render.FmumMuzzlePose.smokeOrigin(new Matrix4f(chain).mul(com.flansmod.client.render.FmumMuzzlePose.smoke(gun,model,chamber)),chamber?sight.roll():spec.freeRotate!=0?37F:0F);
                var smoke=new com.flansmod.client.model.FmumSmoke(spec.model,spec.texture+"0",origin,spec.scale,spec.scaleEx,spec.vanishConst);
                for(int i=0;i<effectsTick;i++)smoke.tick();
                var rig=ModelCache.rigForReference(spec.model,1F);
                if(rig!=null && smoke.alive())parts.add(new Part(smoke.pose(),rig,skin(root,smoke.texture),0xFFFFFFFF,smoke.alpha,false,true));
                System.out.printf("%s smoke alpha=%.4f scale=%.4f origin=%s%n",chamber?"chamber":"muzzle",smoke.alpha,smoke.scale(),origin.getTranslation(new Vector3f()));
            }
        }

        int caseTick=Integer.parseInt(System.getProperty("fp.caseTick","-1"));
        if(caseTick>=0 && gun.itsType!=GunType.GunItsType.MAG) {
            var type=BulletType.getBullet(System.getProperty("fp.caseType","556x45mmCasing"));
            if(type==null)throw new IllegalArgumentException("Unknown ejected bullet/case");
            var casing=new com.flansmod.client.model.FmumCasing(type,model,chain,Boolean.getBoolean("fp.manualEject"),Long.getLong("fp.caseSeed",7L),sight.roll());
            for(int i=0;i<caseTick;i++)casing.tick(null,0,0,0);
            var origin=com.flansmod.client.model.FmumCasing.origin(model,new Matrix4f(chain));
            var rig=ModelCache.rigForReference(type.bulletModel,1F);
            if(rig!=null && casing.alive())parts.add(new Part(new Matrix4f().translate(origin).mul(casing.pose(1F)),rig,skin(root,type.bulletTexture),0xFFFFFFFF));
            System.out.printf("case=%s tick=%d origin=%s displacement=%s spin=%.4f lifetime=%d%n",type.shortName,caseTick,origin,casing.position,casing.spin,casing.lifetime);
        }
        render(parts, out);
    }

    static void addArm(List<Part> parts, Path root, ModelGeometry.Model model, String modelRef, String texture,
                       String posKey, String rotKey, String scaleKey, Matrix4f chain,
                       String[] rotOverride, float exRx, float exRy, float exRz, int tint) {
        if (modelRef == null || modelRef.isBlank() || modelRef.equalsIgnoreCase("None")) return;
        BakedRig rig = ModelCache.rigForReference(modelRef, 1F);
        if (rig == null || rig.quads.isEmpty()) return;
        float[] pos = model == null ? null : model.fmumPose.get(posKey);
        float[] rot = model == null ? null : model.fmumPose.get(rotKey);
        float[] scale = model == null ? null : model.fmumPose.get(scaleKey);

        float rx = rotOverride[0].isEmpty() ? (rot == null ? 0 : rot[0]) : Float.parseFloat(rotOverride[0]);
        float ry = rotOverride[1].isEmpty() ? (rot == null ? 0 : rot[1]) : Float.parseFloat(rotOverride[1]);
        float rz = rotOverride[2].isEmpty() ? (rot == null ? 0 : rot[2]) : Float.parseFloat(rotOverride[2]);

        Matrix4f m = new Matrix4f(chain);
        if (pos != null) m.translate(pos[0], pos[1], pos[2]);
        m.rotateX((float) Math.toRadians(rx));
        m.rotateY((float) Math.toRadians(ry));
        m.rotateZ((float) Math.toRadians(rz));
        if (exRx != 0) m.rotateX((float) Math.toRadians(exRx));
        if (exRy != 0) m.rotateY((float) Math.toRadians(exRy));
        if (exRz != 0) m.rotateZ((float) Math.toRadians(exRz));
        if (scale != null) m.scale(scale[0], scale[1], scale[2]);
        if(Arrays.stream(rotOverride).allMatch(String::isEmpty) && exRx==0 && exRy==0 && exRz==0 && previewGun!=null)
			m=FmumReloadPose.arm(new Matrix4f(chain),model,posKey.startsWith("left"),RELOAD,ACTION,previewGun.modelScale,previewGun);

        BufferedImage tex = null;
        if (texture != null && !texture.isBlank() && !texture.equalsIgnoreCase("None"))
            tex = skin(root, texture);
        else if(!Boolean.parseBoolean(System.getProperty("fp.tintArms", "true"))) {
            try {
                String supplied=System.getProperty("fp.playerSkin");
                if(supplied!=null) tex=ImageIO.read(new File(supplied));
                else try(var input=FPPreview.class.getResourceAsStream("/assets/minecraft/textures/entity/player/wide/steve.png")) {
                    if(input!=null)tex=ImageIO.read(input);
                }
            }catch(Exception e){throw new IllegalStateException("Could not read player skin",e);}
            if(tex!=null) {
                float[] correction=ModelCache.uvScaleForDimensions(rig.animation,tex.getWidth(),tex.getHeight());
                rig=BakedRig.bake(rig.animation,1F,correction[0],correction[1]);
            }
        }
        parts.add(new Part(m, rig, tex, tint));
        Vector3f o = m.transformPosition(new Vector3f());
        System.out.printf("  arm %s %s origin view (%.3f %.3f %.3f) rot=(%.0f %.0f %.0f)+extra(%.0f %.0f %.0f)%n",
                posKey, modelRef, o.x, o.y, o.z, rx, ry, rz, exRx, exRy, exRz);
    }

    static void addNodes(List<Part> parts, Path root, ModifiableType owner,
                         List<ModifiableType.DefaultAttachment> nodes, Matrix4f parent, int depth) {
        if (depth > 8) return;
        for (ModifiableType.DefaultAttachment node : nodes) {
            AttachmentType at = AttachmentType.getAttachment(node.name);
            if (at == null) continue;
            Matrix4f m = new Matrix4f(parent);
            if (node.slot >= 0 && node.slot < owner.slots.length) {
                ModifiableType.AttachableSlot slot = owner.slots[node.slot];
                m.translate(slot.x + node.pos, slot.y, slot.z);
                if (slot.rotate != 0F) m.rotateX((float) Math.toRadians(slot.rotate));
            }
            if(previewGun!=null) {
                var model=ModelCache.rigFor(previewGun,previewGun.modelScale).animation;
                switch(at.itsType.toLowerCase(Locale.ROOT)) {
                    case "bolt" -> m.translate(FmumReloadPose.slide(model,RELOAD,ACTION,0,false)*previewGun.modelScale,0,0);
                    case "charginghandle" -> m.translate(FmumReloadPose.charge(model,RELOAD,ACTION).handle()*previewGun.modelScale,0,0);
                    case "altchargehandle" -> FmumActionPose.pump2(m,model,FmumReloadPose.charge(model,RELOAD,ACTION),previewGun.modelScale,false);
                }
            }
            BakedRig rig = ModelCache.rigFor(at, at.modelScale);
            if (rig != null) parts.add(new Part(new Matrix4f(m), rig, skin(root, at.texture), 0xFFFFFFFF));
            addNodes(parts, root, at, node.children, m, depth + 1);
        }
    }

    static void addSeatedBullets(List<Part> parts,Path root,GunType mag,Matrix4f parent,int rounds) {
        String name=System.getProperty("fp.bullet","5.45x39mm7n39");
        BulletType bullet=InfoType.infoTypes.stream().filter(t -> t instanceof BulletType && name.equalsIgnoreCase(t.shortName)).map(t -> (BulletType)t).findFirst().orElseThrow();
        var rig=ModelCache.rigForReference(bullet.bulletModel,bullet.bulletScale);
        var model=ModelCache.rigFor(mag,mag.modelScale).animation;
        var seats=FmumBulletPose.seats(model,rounds);
        if(rig!=null) for(var seat:seats) parts.add(new Part(new Matrix4f(parent).mul(seat.pose()),rig,skin(root,bullet.bulletTexture),0xFFFFFFFF));
        System.out.printf("seated bullets: magazine=%s rounds=%d visible=%d bullet=%s%n",mag.shortName,rounds,seats.size(),bullet.bulletModel);
    }

    static BufferedImage skin(Path packRoot, String name) {
        if (name == null || name.isBlank()) return null;
        String wanted = (name.toLowerCase(Locale.ROOT).endsWith(".png")
                ? name.substring(0, name.length() - 4) : name).toLowerCase(Locale.ROOT);
        Path skins = packRoot.resolve("assets/flansmod/skins");
        if (!Files.isDirectory(skins)) return null;
        try (var walk = Files.walk(skins)) {
            for (Path p : walk.toList()) {
                if (!p.toString().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                String rel = skins.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (rel.equals(wanted + ".png") || rel.endsWith("/" + wanted + ".png"))
                    return ImageIO.read(p.toFile());
            }
        } catch (Exception ignored) {}
        return null;
    }

    static void render(List<Part> parts, File out) throws Exception {
        if(Boolean.getBoolean("fp.shaderEffects"))parts.sort(Comparator.comparingInt(p->p.effect&&p.glowing?1:0));
        owners=new int[W*H];
        float[] zbuf = new float[W * H];
        Arrays.fill(zbuf, Float.POSITIVE_INFINITY);
        int[] pix = new int[W * H];
        Arrays.fill(pix, 0xFF8899AA);

        for (Part part : parts) {
            float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
            for(var q : part.rig.quads) for(var p : q.positions()) {
                Vector3f v=part.m.transformPosition(new Vector3f(p));
                minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);
                minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);
                minZ=Math.min(minZ,v.z);maxZ=Math.max(maxZ,v.z);
            }
            if(part.tint != 0xFFFFFFFF) System.out.printf("arm bounds (%f,%f,%f)..(%f,%f,%f)%n",minX,minY,minZ,maxX,maxY,maxZ);
            for (BakedRig.Quad quad : part.rig.quads) {
                Vector3f[] v = new Vector3f[4];
                for (int i = 0; i < 4; i++) {
                    float[] p = quad.positions()[i];
                    v[i] = part.m.transformPosition(new Vector3f(p[0], p[1], p[2]));
                }
                Vector3f e1 = new Vector3f(v[1]).sub(v[0]);
                Vector3f e2 = new Vector3f(v[3]).sub(v[0]);
                Vector3f n = e1.cross(e2).normalize();
                Vector3f mid = new Vector3f(v[0]).add(v[1]).add(v[2]).add(v[3]).mul(0.25F);
                float lambert = Math.abs(n.dot(new Vector3f(mid).negate().normalize()));
                float shade = part.glowing ? 1F : 0.35F + 0.65F * lambert;
                float[][] uv = quad.uvs();
                for (int tri = 0; tri < 2; tri++) {
                    int a = 0, b = tri == 0 ? 1 : 2, c = tri == 0 ? 2 : 3;
                    clipped(v[a], v[b], v[c], uv[a], uv[b], uv[c], shade, part, zbuf, pix);
                }
            }
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, W, H, pix, 0, W);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.drawLine(W/2-5,H/2,W/2+5,H/2);
        g.drawLine(W/2,H/2-5,W/2,H/2+5);
        g.dispose();
        ImageIO.write(img, "png", out);
        for(int color:new int[]{0xFFFFFFFF,0xFF6FA8DC,0xFFE06666}) {
            int count=0, minX=W,minY=H,maxX=-1,maxY=-1;
            for(int i=0;i<owners.length;i++)if(owners[i]==color){count++;minX=Math.min(minX,i%W);minY=Math.min(minY,i/W);maxX=Math.max(maxX,i%W);maxY=Math.max(maxY,i/W);}
            System.out.printf("visible %s pixels=%d bounds=(%d,%d)..(%d,%d)%n",color==0xFFFFFFFF?"gun":color==0xFF6FA8DC?"leftArm":"rightArm",count,minX,minY,maxX,maxY);
        }
        System.out.println("wrote " + out);
    }

    record ClipVertex(Vector3f p, float[] uv) {}
    static void clipped(Vector3f a,Vector3f b,Vector3f c,float[] au,float[] bu,float[] cu,
                        float shade,Part part,float[] zbuf,int[] pix) {
        List<ClipVertex> src=List.of(new ClipVertex(a,au),new ClipVertex(b,bu),new ClipVertex(c,cu));
        List<ClipVertex> dst=new ArrayList<>();
        for(int i=0;i<src.size();i++) {
            ClipVertex from=src.get(i),to=src.get((i+1)%src.size());
            boolean fi=from.p.z<=-NEAR,ti=to.p.z<=-NEAR;
            if(fi)dst.add(from);
            if(fi!=ti) {
                float t=(-NEAR-from.p.z)/(to.p.z-from.p.z);
                dst.add(new ClipVertex(new Vector3f(from.p).lerp(to.p,t),new float[]{
                    from.uv[0]+(to.uv[0]-from.uv[0])*t,from.uv[1]+(to.uv[1]-from.uv[1])*t}));
            }
        }
        for(int i=1;i+1<dst.size();i++)raster(dst.get(0).p,dst.get(i).p,dst.get(i+1).p,
                dst.get(0).uv,dst.get(i).uv,dst.get(i+1).uv,shade,part,zbuf,pix);
    }

    static void raster(Vector3f p0, Vector3f p1, Vector3f p2, float[] uv0, float[] uv1, float[] uv2,
                       float shade, Part part, float[] zbuf, int[] pix) {
        float[][] s = new float[3][3];
        Vector3f[] p = { p0, p1, p2 };
        float[][] uv = { uv0, uv1, uv2 };
        for (int i = 0; i < 3; i++) {
            float ndcX = p[i].x / (-p[i].z * TAN_HALF * ASPECT);
            float ndcY = p[i].y / (-p[i].z * TAN_HALF);
            s[i][0] = (ndcX + 1F) * 0.5F * W;
            s[i][1] = (1F - (ndcY + 1F) * 0.5F) * H;
            s[i][2] = -p[i].z;
        }
        int minX = (int) Math.max(0, Math.floor(Math.min(s[0][0], Math.min(s[1][0], s[2][0]))));
        int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(s[0][0], Math.max(s[1][0], s[2][0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(s[0][1], Math.min(s[1][1], s[2][1]))));
        int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(s[0][1], Math.max(s[1][1], s[2][1]))));
        if (maxX < minX || maxY < minY) return;
        float area = edge(s[0], s[1], s[2]);
        if (Math.abs(area) < 1e-6F) return;
        float iw0 = 1F / s[0][2], iw1 = 1F / s[1][2], iw2 = 1F / s[2][2];
        int tint = part.effect || Boolean.parseBoolean(System.getProperty("fp.tintArms", "true")) ? part.tint : 0xFFFFFFFF;
        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5F, py = y + 0.5F;
                float w0 = edge(s[1], s[2], px, py) / area;
                float w1 = edge(s[2], s[0], px, py) / area;
                float w2 = edge(s[0], s[1], px, py) / area;
                if (w0 < 0F || w1 < 0F || w2 < 0F) continue;
                float iw = w0 * iw0 + w1 * iw1 + w2 * iw2;
                float depth = 1F / iw;
                int idx = y * W + x;
                boolean postFlash=part.effect && part.glowing && Boolean.getBoolean("fp.shaderEffects");
                if (depth < 0F || depth > 40F || (!postFlash && depth >= zbuf[idx])) continue;
                int c = 0xFF808080;
                if (part.tex != null) {
                    float u = (w0 * uv[0][0] * iw0 + w1 * uv[1][0] * iw1 + w2 * uv[2][0] * iw2) / iw;
                    float vv = (w0 * uv[0][1] * iw0 + w1 * uv[1][1] * iw1 + w2 * uv[2][1] * iw2) / iw;
                    if(part.effect && Boolean.getBoolean("fp.shaderEffects") && !Boolean.getBoolean("fp.legacyShaderEffects"))c=linearSample(part.tex,u,vv);
                    else {
                        int tx = Math.floorMod((int)Math.floor(u * part.tex.getWidth()),part.tex.getWidth());
                        int ty = Math.floorMod((int)Math.floor(vv * part.tex.getHeight()),part.tex.getHeight());
                        c = part.tex.getRGB(tx, ty);
                    }
                    if (((c >>> 24) & 0xFF) < 26) continue;
                }
                // Live emissive flashes write colour only; ordinary smoke also writes depth.
                if(!part.effect || !part.glowing)zbuf[idx] = depth;
                owners[idx] = part.tint;
                int r = (int) Math.min(255, ((c >> 16) & 0xFF) * shade * ((tint >> 16) & 0xFF) / 255F);
                int g = (int) Math.min(255, ((c >> 8) & 0xFF) * shade * ((tint >> 8) & 0xFF) / 255F);
                int b = (int) Math.min(255, (c & 0xFF) * shade * (tint & 0xFF) / 255F);
                if(part.effect) {
                    float alpha=part.alpha*((c>>>24)&255)/255F;
                    int behind=pix[idx];
                    r=(int)(r*alpha+((behind>>16)&255)*(1-alpha));
                    g=(int)(g*alpha+((behind>>8)&255)*(1-alpha));
                    b=(int)(b*alpha+(behind&255)*(1-alpha));
                }
                pix[idx] = (r << 16) | (g << 8) | b;
            }
    }

    // Match GL_LINEAR texel-centre interpolation for the shader compatibility effect textures.
    static int linearSample(BufferedImage texture,float u,float v) {
        float x=u*texture.getWidth()-.5F,y=v*texture.getHeight()-.5F;
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);float fx=x-ix,fy=y-iy;
        int[] colours={texture.getRGB(Math.floorMod(ix,texture.getWidth()),Math.floorMod(iy,texture.getHeight())),
            texture.getRGB(Math.floorMod(ix+1,texture.getWidth()),Math.floorMod(iy,texture.getHeight())),
            texture.getRGB(Math.floorMod(ix,texture.getWidth()),Math.floorMod(iy+1,texture.getHeight())),
            texture.getRGB(Math.floorMod(ix+1,texture.getWidth()),Math.floorMod(iy+1,texture.getHeight()))};
        int colour=0;
        for(int shift:new int[]{0,8,16,24}) {
            float top=((colours[0]>>>shift)&255)*(1-fx)+((colours[1]>>>shift)&255)*fx;
            float bottom=((colours[2]>>>shift)&255)*(1-fx)+((colours[3]>>>shift)&255)*fx;
            colour|=(int)(top*(1-fy)+bottom*fy)<<shift;
        }
        return colour;
    }

    static float edge(float[] a, float[] b, float px, float py) {
        return (px - a[0]) * (b[1] - a[1]) - (py - a[1]) * (b[0] - a[0]);
    }

    static float edge(float[] a, float[] b, float[] c) {
        return edge(a, b, c[0], c[1]);
    }
}
