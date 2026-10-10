package com.flansmod.common;

/** FMUM's take-out and reversible operation progress, independent of rendering and input. */
public final class GunActionState
{
	public enum Operation { NONE, CHECK_AMMO, CHECK_CHAMBER, CHECK_CHAMBER_BC, REST, UNREST }
	public record Frame(Operation operation, float progress, float takeOut) {
		public static final Frame IDLE = new Frame(Operation.NONE, 0F, 0F);
	}
	public Operation operation = Operation.NONE, queued = Operation.NONE;
	public float progress, lastProgress, takeOut, lastTakeOut;
	public boolean returning, manualRest;
	private float duration, queuedDuration;
	/** Bits: outward sound, return sound, inspection midpoint. Consumed once by the controller. */
	public int events;
	private boolean sounded;

	public void equip(float length) {
		reset();
		duration = Math.max(0F, length);
		takeOut = lastTakeOut = duration > 0F ? 1F : 0F;
	}
	public void reset() {
		operation = queued = Operation.NONE;
		progress = lastProgress = takeOut = lastTakeOut = duration = queuedDuration = 0F;
		returning = manualRest = sounded = false; events = 0;
	}
	public boolean ready() { return takeOut == 0F && lastTakeOut == 0F && operation == Operation.NONE; }
	public boolean checking() { return operation == Operation.CHECK_AMMO || operation == Operation.CHECK_CHAMBER || operation == Operation.CHECK_CHAMBER_BC; }
	public void request(Operation next, float length) {
		if(length <= 0F) return;
		manualRest = false;
		queued = next; queuedDuration = length;
		if(checking() && !returning) { returning = true; sounded = false; }
		if(operation == Operation.REST) { operation = Operation.UNREST; manualRest = false; }
	}
	public void resume() {
		manualRest = false;
		queued = Operation.NONE;
		if(operation == Operation.REST) operation = Operation.UNREST;
		if(checking() && !returning) { returning = true; sounded = false; }
	}
	public void toggleRest() {
		if(operation==Operation.REST) { operation=Operation.UNREST;manualRest=false;return; }
		manualRest = !manualRest;
		if(manualRest && operation == Operation.UNREST) operation = Operation.REST;
		if(!manualRest && operation == Operation.REST) operation = Operation.UNREST;
		if(manualRest && checking()) returning = true;
	}
	public void tick(float takeOutTime, float restTime, float unrestTime, boolean nearWall,
			boolean sprinting, boolean otherBusy, float soundOut, float soundBack)
	{
		events = 0; lastProgress = progress; lastTakeOut = takeOut;
		if(lastTakeOut > 0F) { takeOut = Math.max(0F, takeOut - 1F / Math.max(1F, takeOutTime)); return; }
		boolean resting = manualRest || nearWall;
		if(operation == Operation.REST && !resting) operation = Operation.UNREST;
		if(operation == Operation.UNREST && nearWall) operation = Operation.REST;
		if(operation == Operation.NONE && !sprinting && !otherBusy) {
			if(queued != Operation.NONE) {
				operation = queued; duration = queuedDuration; queued = Operation.NONE;
				progress = lastProgress = 0F; returning = sounded = false;
			} else if(resting) operation = Operation.REST;
		}
		if(checking()) {
			if(!returning) {
				progress += 2F / Math.max(1F, duration);
				if(progress > 1F) { progress = 1F; returning = true; sounded = false; events |= 4; }
				else if(!sounded && progress >= soundOut) { sounded = true; events |= 1; }
			} else {
				progress -= 2F / Math.max(1F, duration);
				if(progress < 0F) { operation = Operation.NONE; progress = lastProgress = 0F; }
				else if(!sounded && progress <= soundBack) { sounded = true; events |= 2; }
			}
		} else if(operation == Operation.REST) progress = Math.min(1F, progress + 1F / Math.max(1F, manualRest ? restTime : unrestTime));
		else if(operation == Operation.UNREST) {
			progress -= 1F / Math.max(1F, unrestTime);
			if(progress < 0F) { operation = Operation.NONE; progress = lastProgress = 0F; }
		}
	}
	public Frame frame(float partial) {
		return new Frame(operation, lastProgress + (progress - lastProgress) * partial,
				lastTakeOut + (takeOut - lastTakeOut) * partial);
	}
}
