import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.Textures;
import com.flansmod.common.types.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

/** EFT material audit; no Minecraft instance, content-pack execution, or desktop input. */
public final class MaterialAudit {
    static final int CW=220, CH=184, COLS=5, PAGE=30;
    static OfflinePack pack, original;
    static Path out;
    static class Row {
        String category, name, role, model, texture, status="OK", compare="", note="";
        Path file;
        BakedRig rig;
        BufferedImage image;
        int w,h,dw,dh,texels,quads;
        double opaque=Double.NaN,rawOpaque=Double.NaN,luma=Double.NaN,outside=0;
        float[] correction={1F,1F};
        String id() { return name+" / "+role; }
        void flag(String s,String reason) { if(status.equals("OK")||s.equals("HIGH"))status=s; note+=(note.isEmpty()?"":"; ")+reason; }
    }
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        pack=new OfflinePack(Paths.get(args[0]));
        original=new OfflinePack(Paths.get(args[1]));
        out=Paths.get(args[2]); Files.createDirectories(out);
        pack.readTypes();
        List<Row> rows=new ArrayList<>();
        Map<String,Integer> counts=new TreeMap<>();
        for(InfoType t:pack.types) {
            String category=category(t);
            counts.merge(category,1,Integer::sum);
            if(t.texture!=null&&!t.texture.isBlank()) rows.add(inspect(t,category,"base",t.modelString,t.texture,null));
            if(t instanceof ModifiableType m) for(var paint:m.paintjobs)
                if(!Objects.equals(paint.textureName(),t.texture)) rows.add(inspect(t,category,"paint"+paint.index(),t.modelString,paint.textureName(),null));
            if(t instanceof ShootableType b && b.bulletTexture!=null&&!b.bulletTexture.isBlank()) {
                Row flight=inspect(t,category,"bulletModel",b.bulletModel,b.bulletTexture,null);
                if(t instanceof BulletType && (t.modelString==null||t.modelString.isBlank()) && !flight.status.equals("HIGH")) {
                    flight.status="KNOWN";flight.note+=(flight.note.isEmpty()?"":"; ")+"BulletModel-only round: original 1.7.10 does not render in flight";
                }
                rows.add(flight);
            }
            if(t instanceof ArmourType a && a.armourTextureName!=null) {
                int layer=a.type==2?2:1;
                ResourceLocation location=ResourceLocation.fromNamespaceAndPath("flansmod","armor/"+a.armourTextureName.toLowerCase(Locale.ROOT)+"_"+layer+".png");
                rows.add(inspect(t,category,"armour"+layer,null,a.armourTextureName,location));
            }
        }
        // Robust category outliers are review candidates, never reasons to repaint pack art.
        for(String cat:counts.keySet()) {
            double[] values=rows.stream().filter(r->r.category.equals(cat)&&Double.isFinite(r.luma)).mapToDouble(r->r.luma).sorted().toArray();
            if(values.length<5)continue;
            double med=median(values);
            double[] dev=Arrays.stream(values).map(x->Math.abs(x-med)).sorted().toArray();
            double limit=Math.max(30,3*1.4826*median(dev));
            for(Row r:rows) if(r.category.equals(cat)&&Double.isFinite(r.luma)&&Math.abs(r.luma-med)>limit) {
                if(r.texture.toLowerCase(Locale.ROOT).contains("ak74mdefault"))r.note+=(r.note.isEmpty()?"":"; ")+"AK74M dark skin whitelist";
                else if(!r.status.equals("KNOWN"))r.flag("REVIEW",String.format("brightness outlier (category median %.1f, threshold %.1f)",med,limit));
            }
        }
        for(Row r:rows) if(r.texture.toLowerCase(Locale.ROOT).contains("ak74mdefault")&&!r.note.contains("whitelist"))
            r.note+=(r.note.isEmpty()?"":"; ")+"AK74M dark skin whitelist";
        rows.sort(Comparator.comparing((Row r)->r.category).thenComparing(r->r.name).thenComparing(r->r.role));
        writeCsv(rows);
        Map<String,List<String>> sheets=new TreeMap<>();
        for(String cat:counts.keySet()) {
            List<Row> group=rows.stream().filter(r->r.category.equals(cat)).toList();
            List<String> pages=new ArrayList<>();
            for(int i=0;i<group.size();i+=PAGE) {
                String name="materials_"+cat+"_"+(i/PAGE+1)+".png";
                contact(group.subList(i,Math.min(i+PAGE,group.size())),out.resolve(name));pages.add(name);
            }
            sheets.put(cat,pages);
        }
        StringBuilder md=new StringBuilder("# EFT 全包材质对照审计\n\n生成日期：2026-10-04（Asia/Shanghai）。\n\n");
        md.append("运行包：`").append(pack.root).append("`\n\n1.7.10 原包解包目录：`").append(original.root).append("`\n\n");
        md.append("使用实际定义读取器、`Textures.forType(type, resourceExists)` 与运行时共用的单轴 UV 规则。仅离线读取；未启动游戏。\n\n");
        md.append("| 类别 | 定义总数 | 材质条目 | 缺失/损坏/与原包不同 | 待目视复核 |\n|---|---:|---:|---:|---:|\n");
        for(var e:counts.entrySet()) {
            var g=rows.stream().filter(r->r.category.equals(e.getKey())).toList();
            md.append(String.format("| %s | %d | %d | %d | %d |\n",e.getKey(),e.getValue(),g.size(),g.stream().filter(r->r.status.equals("HIGH")).count(),g.stream().filter(r->r.status.equals("REVIEW")).count()));
        }
        long high=rows.stream().filter(r->r.status.equals("HIGH")).count();
        long review=rows.stream().filter(r->r.status.equals("REVIEW")).count();
        md.append(String.format("\n共 %d 条材质记录，高置信异常 %d 条，需复核 %d 条。可烘焙模型 %d 条，字节与原包相同 %d 条。\n\n",rows.size(),high,review,rows.stream().filter(r->r.rig!=null).count(),rows.stream().filter(r->r.compare.equals("IDENTICAL")).count()));
        md.append("## 本轮修复的高置信问题\n\n同一模型数组内的 ModelRendererTurbo 各自拥有贴图偏移，但移植提取器原先只在 Part 上保存最后一个元素的偏移，导致整组网格采样同一块皮肤。现在按 renderer#index 保存并烘焙每个 box 的偏移和声明尺寸，未改贴图文件。对照本轮修复前的审计：AK74M 命中纹素的不透明占比 44.3% → 100%，AK74 为 14.7% → 100%，6P20Sb9Foregrip 为 22.3% → 100%。这些是 UV 采样统计，不能代替实机光照/透明排序验收。\n\n另补 Model700 系列缺文件时的双 Model 前缀回退，四个模型引用已可读取；不改变已有 W44 等名称优先级。下列统计是修复后的结果。\n\n");
        md.append("## 判定与边界\n\n- HIGH：明确缺文件、PNG 无法解码，或运行包材质字节与原始 1.7.10 包不同。模型读取失败单列为 REVIEW。\n- REVIEW：模型 UV 命中的唯一纹素透明比例超过 60%，尺寸不匹配，UV 超界，或类别亮度离群；这类结果不能直接视为移植错误。\n- 使用 alpha ≥ 26 统计可见纹素；扫描每个非零面积面的 UV 矩形并按 repeat 取样，报告唯一被采样纹素的覆盖率和平均亮度。零面积面的 UV 不计入透明度。\n- 枪与附件的 primary/paint 记录使用模型和实际绑定皮肤；bulletModel 单独统计，不把只有 BulletModel 的七种弹药当作缺失的飞行模型修复。\n- 护甲按 1.7.10 的 armor/<name>_1/2.png 规则核查；当前烘焙器不支持继承 ModelBiped 的穿戴模型，因此护甲只预览皮肤，UV 覆盖率记 N/A，不能宣称穿戴效果通过。\n- 缩略图使用烘焙网格的固定斜视角；不模拟光照、透明材质排序、枪的附件装配或所有专用模型的自定义渲染方法。\n- `AK74MDefault` 黑色是原包设计，保留白名单；未更改任何低置信贴图。\n\n");
        md.append("## 异常与待复核项\n\n| 级别 | 类别 / 物品 | 用途 | 透明占比 | 尺寸（声明→实际） | 原包对照 | 原因 |\n|---|---|---|---:|---|---|---|\n");
        for(Row r:rows) if(!r.status.equals("OK")) md.append(String.format("| %s | %s / %s | %s | %s | %dx%d → %dx%d | %s | %s |\n",r.status,r.category,r.name,r.role,pct(1-r.opaque),r.dw,r.dh,r.w,r.h,r.compare,r.note));
        if(high+review==0)md.append("| — | 无异常候选 | — | — | — | — | — |\n");
        md.append("\n## 分类接触表\n\n");
        for(var e:sheets.entrySet()) for(String sheet:e.getValue())md.append("![").append(e.getKey()).append("](").append(sheet).append(")\n\n");
        md.append("逐条记录见 [materials.csv](materials.csv)，包括路径、UV 修正、原始/修正后不透明比例、唯一采样纹素数、亮度及原包字节对照。\n");
        Files.writeString(out.resolve("材质审计报告.md"),md);
        System.out.printf("rows=%d high=%d review=%d identical=%d baked=%d%n",rows.size(),high,review,rows.stream().filter(r->r.compare.equals("IDENTICAL")).count(),rows.stream().filter(r->r.rig!=null).count());
        for(Row r:rows)if(!r.status.equals("OK"))System.out.printf("%s %s/%s %s %s%n",r.status,r.category,r.id(),pct(1-r.opaque),r.note);
    }
    static String category(InfoType t) {
        if(t instanceof GunType)return "guns";
        if(t instanceof AttachmentType)return "attachments";
        if(t instanceof BulletType)return "bullets";
        if(t instanceof ArmourType)return "armour";
        if(t instanceof GrenadeType)return "grenades";
        if(t instanceof ToolType)return "tools";
        return "other";
    }
    static Row inspect(InfoType type,String cat,String role,String model,String texture,ResourceLocation explicit) {
        Row r=new Row(); r.category=cat;r.name=type.shortName;r.role=role;r.model=model;r.texture=texture;
        if(type instanceof BulletType && role.equals("base") && (model==null||model.isBlank())) {
            r.compare="NOT_BOUND";r.status="KNOWN";r.note="no item Model; inherited defaultBullet skin is never bound (known seven BulletModel-only definitions)";return r;
        }
        try {
            InfoType lookup=new InfoType(new TypeFile(EnumType.part,"audit",false));lookup.texture=texture;
            ResourceLocation id=explicit!=null?explicit:Textures.forType(lookup,pack::has);
            r.file=pack.path(id);
            if(r.file==null) { r.flag("HIGH","missing "+id);return r; }
            r.image=ImageIO.read(r.file.toFile());
            if(r.image==null) {r.flag("HIGH","not a decodable PNG");return r;}
            r.w=r.image.getWidth();r.h=r.image.getHeight();
            Path orig=original.path(id);
            if(orig==null){r.compare="ORIGINAL_MISSING";r.flag("REVIEW","not found at same path in original pack");}
            else if(MessageDigest.isEqual(sha(r.file),sha(orig)))r.compare="IDENTICAL";
            else {r.compare="DIFFERENT";r.flag("HIGH","runtime skin bytes differ from original");}
            if(model!=null&&!model.isBlank()&&!model.equalsIgnoreCase("none")) {
                InfoType modelType=new InfoType(new TypeFile(EnumType.part,"audit-model",false));modelType.modelString=model;
                ModelGeometry.Model geo=ModelCache.load(modelType);
                if(geo==null){r.flag("REVIEW","model unavailable");}
                else {
                    r.dw=geo.sheetX;r.dh=geo.sheetY;
                    for(var p:geo.parts.values()){r.dw=Math.max(r.dw,p.sheetWidth);r.dh=Math.max(r.dh,p.sheetHeight);}
                    r.correction=ModelCache.uvScaleForDimensions(geo,r.w,r.h);
                    r.rig=BakedRig.bake(geo,1F,r.correction[0],r.correction[1]);r.quads=r.rig.quads.size();
                    Stats raw=sample(BakedRig.bake(geo,1F),r.image);r.rawOpaque=raw.opaque;
                    Stats corrected=sample(r.rig,r.image);r.opaque=corrected.opaque;r.luma=corrected.luma;r.texels=corrected.texels;r.outside=corrected.outside;
                    if(r.dw!=r.w||r.dh!=r.h)r.flag("REVIEW",String.format("dimension mismatch; uv scale=(%.3f,%.3f)",r.correction[0],r.correction[1]));
                    if(Double.isFinite(r.opaque)&&r.opaque<.4)r.flag("REVIEW","more than 60% sampled texels transparent");
                    if(r.outside>.05)r.flag("REVIEW",String.format("%.1f%% UV vertices outside sheet",r.outside*100));
                    if(r.rig.skippedPrisms>0)r.flag("REVIEW","skipped prisms="+r.rig.skippedPrisms);
                }
            }
            if(!Double.isFinite(r.luma))r.luma=imageLuma(r.image);
        }catch(Exception e){r.flag("REVIEW",e.toString());}
        return r;
    }
    record Stats(double opaque,double luma,int texels,double outside){}
    static Stats sample(BakedRig rig,BufferedImage img) {
        int w=img.getWidth(),h=img.getHeight(),outside=0,vertices=0;
        BitSet hit=new BitSet(w*h);
        for(var q:rig.quads) {
            Vector3f a=new Vector3f(q.positions()[1]).sub(new Vector3f(q.positions()[0]));
            Vector3f b=new Vector3f(q.positions()[3]).sub(new Vector3f(q.positions()[0]));
            if(a.cross(b).lengthSquared()<1e-16)continue;
            float u0=Float.POSITIVE_INFINITY,v0=u0,u1=-u0,v1=-u0;
            for(float[] uv:q.uvs()){u0=Math.min(u0,uv[0]);u1=Math.max(u1,uv[0]);v0=Math.min(v0,uv[1]);v1=Math.max(v1,uv[1]);vertices++;if(uv[0]<0||uv[0]>1||uv[1]<0||uv[1]>1)outside++;}
            int xa=(int)Math.floor(u0*w),xb=Math.max(xa+1,(int)Math.ceil(u1*w));
            int ya=(int)Math.floor(v0*h),yb=Math.max(ya+1,(int)Math.ceil(v1*h));
            // UV rectangles are axis aligned in baked box models. Cap repeats to one full sheet.
            xb=Math.min(xb,xa+w);yb=Math.min(yb,ya+h);
            for(int y=ya;y<yb;y++)for(int x=xa;x<xb;x++)hit.set(Math.floorMod(y,h)*w+Math.floorMod(x,w));
        }
        int opaque=0;double luma=0;
        for(int i=hit.nextSetBit(0);i>=0;i=hit.nextSetBit(i+1)) {
            int c=img.getRGB(i%w,i/w);if((c>>>24)<26)continue;opaque++;luma+=luma(c);
        }
        int n=hit.cardinality();return new Stats(n==0?Double.NaN:(double)opaque/n,opaque==0?Double.NaN:luma/opaque,n,vertices==0?0:(double)outside/vertices);
    }
    static double luma(int c){return .2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255);}
    static double imageLuma(BufferedImage img) {double sum=0;int n=0;for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++){int c=img.getRGB(x,y);if((c>>>24)<26)continue;sum+=luma(c);n++;}return n==0?Double.NaN:sum/n;}
    static byte[] sha(Path p)throws Exception{return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));}
    static double median(double[] sorted){int n=sorted.length;return n%2==0?(sorted[n/2-1]+sorted[n/2])/2:sorted[n/2];}
    static String pct(double v){return Double.isFinite(v)?String.format("%.1f%%",v*100):"N/A";}
    static String csv(Object v){return "\""+String.valueOf(v).replace("\"","\"\"")+"\"";}
    static void writeCsv(List<Row> rows)throws Exception {
        StringBuilder s=new StringBuilder("category,name,role,model,texture,path,width,height,declaredWidth,declaredHeight,uvU,uvV,opaqueRaw,opaqueCorrected,uniqueTexels,luma,uvOutside,quads,original,status,note\n");
        for(Row r:rows){Object[] cols={r.category,r.name,r.role,r.model,r.texture,r.file,r.w,r.h,r.dw,r.dh,r.correction[0],r.correction[1],r.rawOpaque,r.opaque,r.texels,r.luma,r.outside,r.quads,r.compare,r.status,r.note};for(int i=0;i<cols.length;i++){if(i>0)s.append(',');s.append(csv(cols[i]));}s.append('\n');}
        Files.writeString(out.resolve("materials.csv"),s);
    }
    static void contact(List<Row> rows,Path target)throws Exception {
        int nr=(rows.size()+COLS-1)/COLS;
        BufferedImage sheet=new BufferedImage(CW*COLS,CH*nr,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,11));
        for(int i=0;i<rows.size();i++) {
            Row r=rows.get(i);int x=(i%COLS)*CW,y=(i/COLS)*CH;
            g.setColor(new Color(224,230,237));g.fillRect(x,y,CW,CH);
            g.setColor(new Color(185,195,205));g.drawRect(x,y,CW-1,CH-1);
            if(r.image!=null) {
                if(r.rig!=null&&!r.rig.quads.isEmpty())g.drawImage(thumb(r),x+5,y+5,null);
                else {double scale=Math.min(210.0/r.w,132.0/r.h);int w=(int)(r.w*scale),h=(int)(r.h*scale);g.drawImage(r.image,x+(CW-w)/2,y+5+(132-h)/2,w,h,null);}
            }
            g.setColor(r.status.equals("HIGH")?new Color(180,20,20):r.status.equals("REVIEW")?new Color(150,90,0):Color.DARK_GRAY);
            String name=r.name+" / "+r.role;
            g.drawString(name.length()>32?name.substring(0,32):name,x+6,y+150);
            g.drawString(r.status+"  "+r.w+"x"+r.h+" alpha "+pct(r.opaque),x+6,y+166);
            r.image=null; // Reload for later rows sharing a skin; release large PNGs after output.
        }
        g.dispose();ImageIO.write(sheet,"png",target.toFile());
    }
    static BufferedImage thumb(Row r) {
        int w=210,h=132;BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        int[] pixels=new int[w*h];Arrays.fill(pixels,0xD6DFE7);float[] depth=new float[w*h];Arrays.fill(depth,Float.POSITIVE_INFINITY);
        Matrix4f view=new Matrix4f().rotateX((float)Math.toRadians(15)).rotateY((float)Math.toRadians(-25));
        List<Vector3f[]> qs=new ArrayList<>();float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(var q:r.rig.quads){Vector3f[] vs=new Vector3f[4];for(int i=0;i<4;i++){vs[i]=view.transformPosition(new Vector3f(q.positions()[i]));minX=Math.min(minX,vs[i].x);maxX=Math.max(maxX,vs[i].x);minY=Math.min(minY,vs[i].y);maxY=Math.max(maxY,vs[i].y);}qs.add(vs);}
        float scale=Math.min((w-12)/Math.max(.0001F,maxX-minX),(h-12)/Math.max(.0001F,maxY-minY));
        float cx=(minX+maxX)/2,cy=(minY+maxY)/2;
        for(int q=0;q<qs.size();q++) {
            Vector3f[] vs=qs.get(q);Vector3f n=new Vector3f(vs[1]).sub(vs[0]).cross(new Vector3f(vs[3]).sub(vs[0]));
            if(n.lengthSquared()<1e-16)continue;
            float shade=.45F+.55F*Math.abs(n.normalize().dot(new Vector3f(-.2F,.6F,.8F).normalize()));
            float[][] p=new float[4][3];for(int i=0;i<4;i++){p[i][0]=(vs[i].x-cx)*scale+w/2;p[i][1]=-(vs[i].y-cy)*scale+h/2;p[i][2]=vs[i].z;}
            var uv=r.rig.quads.get(q).uvs();
            tri(p[0],p[1],p[2],uv[0],uv[1],uv[2],r.image,shade,pixels,depth,w,h);
            tri(p[0],p[2],p[3],uv[0],uv[2],uv[3],r.image,shade,pixels,depth,w,h);
        }
        img.setRGB(0,0,w,h,pixels,0,w);return img;
    }
    static float edge(float[] a,float[] b,float x,float y){return (x-a[0])*(b[1]-a[1])-(y-a[1])*(b[0]-a[0]);}
    static void tri(float[] a,float[] b,float[] c,float[] au,float[] bu,float[] cu,BufferedImage tex,float shade,int[] pix,float[] depth,int w,int h) {
        float area=edge(a,b,c[0],c[1]);if(Math.abs(area)<1e-6)return;
        int xa=Math.max(0,(int)Math.floor(Math.min(a[0],Math.min(b[0],c[0])))),xb=Math.min(w-1,(int)Math.ceil(Math.max(a[0],Math.max(b[0],c[0]))));
        int ya=Math.max(0,(int)Math.floor(Math.min(a[1],Math.min(b[1],c[1])))),yb=Math.min(h-1,(int)Math.ceil(Math.max(a[1],Math.max(b[1],c[1]))));
        for(int y=ya;y<=yb;y++)for(int x=xa;x<=xb;x++) {
            float wa=edge(b,c,x+.5F,y+.5F)/area,wb=edge(c,a,x+.5F,y+.5F)/area,wc=1-wa-wb;
            if(wa<0||wb<0||wc<0)continue;float z=wa*a[2]+wb*b[2]+wc*c[2];int at=y*w+x;if(z>=depth[at])continue;
            float u=wa*au[0]+wb*bu[0]+wc*cu[0],v=wa*au[1]+wb*bu[1]+wc*cu[1];
            int color=tex.getRGB(Math.floorMod((int)Math.floor(u*tex.getWidth()),tex.getWidth()),Math.floorMod((int)Math.floor(v*tex.getHeight()),tex.getHeight()));
            if((color>>>24)<26)continue;depth[at]=z;
            pix[at]=((int)(((color>>16)&255)*shade)<<16)|((int)(((color>>8)&255)*shade)<<8)|(int)((color&255)*shade);
        }
    }
}
