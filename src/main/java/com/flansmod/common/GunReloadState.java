package com.flansmod.common;

import com.flansmod.common.types.GunType;
import java.util.ArrayList;
import java.util.List;

/** Pure FMUM reload clock. Inventory effects are applied by GunReloading on the server. */
public final class GunReloadState {
	public enum Step { NONE,TILT,UNLOAD_MAG,LOAD_MAG,CHARGE,RELEASE,UNTILT,LOAD_AMMO }
	public record Phase(Step step,int ticks,int soundIndex) { }
	public record Frame(Step step,float progress,float tilt) {
		public static final Frame IDLE=new Frame(Step.NONE,0F,0F);
	}
	public record Plan(List<Phase> phases) {
		public Plan { phases=List.copyOf(phases); }
		public int duration() { return phases.stream().mapToInt(Phase::ticks).sum(); }
	}
	private Plan plan=new Plan(List.of());
	private int phase,tick;
	private boolean active;
	public void start(Plan plan) { this.plan=plan;phase=tick=0;active=!plan.phases().isEmpty(); }
	public void reset() { active=false;phase=tick=0; }
	public boolean active() { return active; }
	public Phase phase() { return active?plan.phases().get(phase):new Phase(Step.NONE,1,-1); }
	public int elapsed() { return tick; }
	/** Returns a completed step once; partial tick rendering interpolates within the current step. */
	public Step tick() {
		if(!active || ++tick<phase().ticks()) return Step.NONE;
		Step finished=phase().step();tick=0;
		if(++phase>=plan.phases().size()) active=false;
		return finished;
	}
	public Frame frame(float partial) {
		if(!active) return Frame.IDLE;
		Step step=phase().step();
		float t=Math.clamp((tick+Math.clamp(partial,0F,1F))/phase().ticks(),0F,1F);
		float p=switch(step) {
			case LOAD_MAG,UNTILT -> 1F-t;
			case CHARGE -> t<=.5F?t*2F:(1F-t)*2F;
			default -> t;
		};
		boolean tilts=plan.phases().stream().anyMatch(v->v.step()==Step.TILT);
		float tilt=step==Step.TILT || step==Step.UNTILT?p:step==Step.LOAD_AMMO || !tilts?0F:1F;
		return new Frame(step,p,tilt);
	}
	public static Plan firearm(GunType gun,boolean hasMagazine,boolean hasChamber,boolean caught) {
		return firearm(gun,hasMagazine,hasChamber,caught,false);
	}
	public static Plan firearm(GunType gun,boolean hasMagazine,boolean hasChamber,boolean caught,boolean special) {
		List<Phase> result=new ArrayList<>();
		for(int i=0;i<gun.reloadSteps.size();i++) {
			Step step=switch(gun.reloadSteps.get(i)) {
				case "tilt" -> Step.TILT;
				case "unloadmag" -> Step.UNLOAD_MAG;
				case "loadmag" -> Step.LOAD_MAG;
				case "charge" -> Step.CHARGE;
				case "release" -> Step.RELEASE;
				case "untilt" -> Step.UNTILT;
				default -> Step.NONE;
			};
			if(step==Step.NONE || (step==Step.UNLOAD_MAG && !hasMagazine)
					|| (special && ((step==Step.LOAD_MAG && hasMagazine) || step==Step.CHARGE || step==Step.RELEASE))
					|| ((step==Step.CHARGE || step==Step.RELEASE) && hasChamber)) continue;
			// An unlocked empty bolt still needs charging even if the pack writes "release".
			if(step==Step.RELEASE && !caught) step=Step.CHARGE;
			int duration=i<gun.reloadTimes.size()?gun.reloadTimes.get(i):1;
			result.add(new Phase(step,Math.max(1,duration),i));
		}
		return new Plan(result);
	}
	public static Plan operate(GunType gun,Step step) {
		return new Plan(List.of(new Phase(step,Math.max(1,step==Step.RELEASE?gun.releaseSlideTime:gun.chargeTime),-1)));
	}
	public static Plan compress(GunType mag,int rounds) {
		return compress(mag,rounds,false);
	}
	public static Plan compress(GunType mag,int rounds,boolean creative) {
		List<Phase> result=new ArrayList<>();
		int time=creative?1:mag.reloadTimes.isEmpty()?8:Math.max(1,mag.reloadTimes.get(0));
		for(int i=0;i<rounds;i++) result.add(new Phase(Step.LOAD_AMMO,time,0));
		return new Plan(result);
	}
}
