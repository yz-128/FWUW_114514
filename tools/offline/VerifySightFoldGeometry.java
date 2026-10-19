import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.SightFoldGeometry;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Path;
import java.util.*;

/** Actual five folding pairs: identity bases, rigid intermediate geometry and complete endpoints. */
public final class VerifySightFoldGeometry {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static void near(float a,float b,String label){check(Float.isFinite(a)&&Math.abs(a-b)<2E-4F,label+" actual="+a+" expected="+b);}
    private static List<Vector3f> vertices(List<BakedRig.Quad> quads){var r=new ArrayList<Vector3f>();for(var q:quads)for(var p:q.positions())r.add(new Vector3f(p));return r;}
    private static BakedRig rig(String attachment){var type=AttachmentType.getAttachment(attachment);check(type!=null,"attachment present "+attachment);return ModelCache.rigFor(type,type.modelScale);}

    private static void pair(String from,String to,int fixedPrefix){
        BakedRig source=rig(from),target=rig(to);check(source!=null&&target!=null,"actual endpoint rigs loaded "+from);
        var plan=SightFoldGeometry.plan(source,target);check(plan==SightFoldGeometry.plan(source,target),"rig-pair plan cache "+from);
        var seenSource=Collections.newSetFromMap(new IdentityHashMap<BakedRig.Quad,Boolean>());
        var seenTarget=Collections.newSetFromMap(new IdentityHashMap<BakedRig.Quad,Boolean>());
        int fixed=0,moving=0,renumbered=0,outgoing=0,incoming=0;
        for(var track:plan.tracks()){
            for(var q:track.source())check(seenSource.add(q),"source geometry consumed once "+track.sourceKey());
            for(var q:track.target())check(seenTarget.add(q),"target geometry consumed once "+track.targetKey());
            var a=vertices(track.source());var b=vertices(track.target());
            near(track.sourceAlpha(0),a.isEmpty()?0:1,"initial source alpha");near(track.targetAlpha(0),0,"initial target alpha");
            near(track.sourceAlpha(1),track.matched()?1:0,"final source alpha");near(track.targetAlpha(1),(!track.matched()||track.textureChanged())&&!b.isEmpty()?1:0,"final target alpha");
            if(track.matched()){
                check(a.size()==b.size(),"matched topology "+track.sourceKey());
                if(track.fixed())fixed++;else moving++;
                if(!track.sourceKey().equals(track.targetKey()))renumbered++;
                for(int i=0;i<a.size();i++){
                    near(track.pose(0).transformPosition(new Vector3f(a.get(i))).distance(a.get(i)),0,"exact source endpoint");
                    near(track.pose(1).transformPosition(new Vector3f(a.get(i))).distance(b.get(i)),0,"verified target endpoint");
                    near(track.targetPose(1).transformPosition(new Vector3f(b.get(i))).distance(b.get(i)),0,"target style endpoint is identity");
                    near(track.targetPose(0).transformPosition(new Vector3f(b.get(i))).distance(a.get(i)),0,"target style starts at exact source geometry");
                }
                float length=a.getFirst().distance(a.get(a.size()/2));
                for(float progress:new float[]{.2F,.5F,.8F}){
                    Matrix4f pose=track.pose(progress);check(pose.isFinite(),"finite intermediate rigid pose");
                    near(pose.determinant3x3(),1,"intermediate rigid determinant");
                    near(pose.transformPosition(new Vector3f(a.getFirst())).distance(pose.transformPosition(new Vector3f(a.get(a.size()/2)))),length,
                            "intermediate geometry keeps edge lengths");
                    for(int i=0;i<a.size();i++)near(pose.transformPosition(new Vector3f(a.get(i))).distance(track.targetPose(progress).transformPosition(new Vector3f(b.get(i)))),0,
                            "old/new UV styles occupy same intermediate geometry");
                    if(track.fixed())for(int i=0;i<a.size();i++)near(pose.transformPosition(new Vector3f(a.get(i))).distance(a.get(i)),0,"fixed base never moves");
                }
            }else {if(!a.isEmpty())outgoing++;if(!b.isEmpty())incoming++;near(track.sourceAlpha(.5F),a.isEmpty()?0:.5F,"unmatched old piece fades");near(track.targetAlpha(.5F),b.isEmpty()?0:.5F,"unmatched new piece fades");}
        }
        check(seenSource.size()==source.quads.size(),"complete source endpoint without missing meshes "+from);
        check(seenTarget.size()==target.quads.size(),"complete target endpoint without missing meshes "+to);
        for(int i=0;i<fixedPrefix;i++){
            String key="attachmentModel#"+i;
            check(plan.tracks().stream().anyMatch(t->t.sourceKey().equals(key)&&t.fixed()),"original fixed mount "+from+" "+key);
        }
        check(moving>0,"real fold has moving pieces "+from);
        if(from.startsWith("QBZ"))check(renumbered>0,"re-exported QBZ has verified cross-index correspondences");
        System.out.printf(Locale.ROOT,"%s -> %s: fixed=%d rigidMoving=%d renumbered=%d oldFade=%d newFade=%d sourceQuads=%d targetQuads=%d%n",from,to,fixed,moving,renumbered,outgoing,incoming,source.quads.size(),target.quads.size());
    }

    private static BakedRig fixture(List<BakedRig.Quad> quads,String name){var rig=new BakedRig();rig.quads.addAll(quads);rig.quadsByRenderer.put(name,quads);return rig;}
    private static void hingeFixture(){
        float[][] uv={{0,0},{1,0},{1,1},{0,1}};
        var quad=new BakedRig.Quad(new float[][]{{1,2,0},{1,3,0},{1,3,1},{1,2,1}},uv,1,0,0);
        Vector3f pivot=new Vector3f(0,.125F,.25F);
        Matrix4f end=new Matrix4f().translate(pivot).rotateX((float)Math.toRadians(90)).translate(new Vector3f(pivot).negate());
        float[][] target=new float[4][3];for(int i=0;i<4;i++){var p=end.transformPosition(new Vector3f(quad.positions()[i]));target[i]=new float[]{p.x,p.y,p.z};}
        var source=fixture(List.of(quad),"attachmentModel#3");var folded=fixture(List.of(new BakedRig.Quad(target,uv,1,0,0)),"attachmentModel#17");
        var track=SightFoldGeometry.plan(source,folded).tracks().getFirst();check(track.matched(),"known reindexed hinge matches");
        near(track.pose(.5F).transformPosition(new Vector3f(pivot)).distance(pivot),0,"hinge stays fixed at half phase, not a linearly interpolated translation");
        Vector3f expected=new Matrix4f().translate(pivot).rotateX((float)Math.toRadians(45)).translate(new Vector3f(pivot).negate()).transformPosition(new Vector3f(quad.positions()[0]));
        near(track.pose(.5F).transformPosition(new Vector3f(quad.positions()[0])).distance(expected),0,"known 45-degree midpoint follows circular arc");
        float[][] stretched={{1,2,0},{1,4,0},{1,4,1},{1,2,1}};
        var different=fixture(List.of(new BakedRig.Quad(stretched,uv,1,0,0)),"attachmentModel#3");
        var fallback=SightFoldGeometry.plan(source,different);check(fallback.tracks().size()==2&&fallback.tracks().stream().noneMatch(SightFoldGeometry.Track::matched),"nonrigid shape change safely cross-fades rather than stretches");
    }
    public static void main(String[]args)throws Exception{
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();hingeFixture();
        pair("G33Enabled","G33Disabled",41);pair("G33Disabled","G33Enabled",41);
        pair("MP7Frontsight","MP7FrontsightFolded",14);pair("MP7Rearsight","MP7RearsightFolded",21);
        pair("QBZ191FrontSight","QBZ191FrontSightFolded",14);pair("QBZ191RearSight","QBZ191RearSightFolded",0);
        if(args.length>1) {
            new OfflinePack(Path.of(args[1])).readTypes();
            pair("G33","G33Flipped",0);pair("G33Flipped","G33",0);
            pair("CICIS4FrontSight","CICIS4FrontSightFlipped",0);
            pair("HK416FrontSight","HK416FrontSightFlipped",0);
            pair("CICIS4RearSight","CICIS4RearSight2",0);
            pair("CICIS4RearSight2","CICIS4RearSightFlipped",0);
            pair("CICIS4RearSightFlipped","CICIS4RearSight",0);
        }
        System.out.println("PASS: "+checks+" assertions; real five endpoint pairs, bidirectional G33, fixed bases, reindexed QBZ matches, exact mesh conservation, rigid intermediate poses and nonrigid fallback. Offline only; no game/OpenGL executed.");
    }
}
