import com.flansmod.common.ReloadKeyState;

/** Input-clock values only; never sends keyboard or mouse events to any application. */
public final class VerifyReloadInput {
	static void check(boolean ok,String text) { if(!ok) throw new AssertionError(text); }
	public static void main(String[] args) {
		Object mag=new Object(),gun=new Object();var key=new ReloadKeyState();
		check(key.update(true,true,0,mag)==0 && key.update(false,true,0,mag)==0,"short tap cannot bulk fill magazine");
		for(int i=0;i<10;i++) check(key.update(false,true,0,mag)==0,"short tap has no deferred compression");
		for(int i=1;i<=ReloadKeyState.HOLD_TICKS;i++) check(key.update(true,true,0,mag)==(i==ReloadKeyState.HOLD_TICKS?ReloadKeyState.HOLD:0),"hold threshold");
		for(int i=0;i<100;i++) check(key.update(true,true,0,mag)==0,"holding sends start once, never double reload");
		check(key.update(false,true,0,mag)==ReloadKeyState.RELEASE,"release stops compression once");
		check(key.update(false,true,0,mag)==0,"release does not repeat");
		for(int i=0;i<ReloadKeyState.HOLD_TICKS;i++) key.update(true,true,0,mag);
		check(key.update(true,true,1,mag)==ReloadKeyState.RELEASE,"slot switch stops compression");
		for(int i=0;i<10;i++) check(key.update(true,true,1,mag)==0,"switch with held R must await release");
		key.update(false,true,1,mag);key.reset();
		check(key.update(true,false,0,gun)==0,"first press waits for second press");
		for(int i=1;i<=ReloadKeyState.DOUBLE_TICKS;i++) check(key.update(false,false,0,gun)==(i==ReloadKeyState.DOUBLE_TICKS?ReloadKeyState.NORMAL:0),"single reload decision window");
		key.reset();key.update(true,false,0,gun);key.update(false,false,0,gun);
		check(key.update(true,false,0,gun)==ReloadKeyState.SPECIAL,"double R selects special only");
		for(int i=0;i<12;i++) check(key.update(false,false,0,gun)==0,"double R cannot also run normal reload");
		key.reset();int normals=0;
		for(int i=0;i<40;i++) { int command=key.update(true,false,0,gun);check((command&ReloadKeyState.SPECIAL)==0,"continuous hold is not double R");if((command&ReloadKeyState.NORMAL)!=0) normals++; }
		check(normals==1,"held firearm R reloads only once");
		key.reset();check(key.update(false,false,0,gun,2)==ReloadKeyState.SPECIAL,"two observed real presses inside one tick retained");
		key.reset();key.update(true,false,0,gun);key.update(false,false,-1,null);
		for(int i=0;i<10;i++) check(key.update(false,false,0,gun)==0,"opening GUI/discarding held gun clears pending reload");
		System.out.println("PASS: short tap cannot compress; 4-tick hold starts once; release and slot change stop; 6-tick double R emits special without normal reload; held/repeated R cannot double-trigger; sub-tick real presses and GUI reset covered. No application input generated.");
	}
}
