/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2021 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.zrp200.rkpd2.actors.mobs;


import com.zrp200.rkpd2.Assets;
import com.zrp200.rkpd2.Badges;
import com.zrp200.rkpd2.Challenges;
import com.zrp200.rkpd2.Dungeon;
import com.zrp200.rkpd2.actors.Actor;
import com.zrp200.rkpd2.actors.Char;
import com.zrp200.rkpd2.actors.buffs.Barrier;
import com.zrp200.rkpd2.actors.buffs.Buff;
import com.zrp200.rkpd2.actors.buffs.Doom;
import com.zrp200.rkpd2.actors.buffs.LifeLink;
import com.zrp200.rkpd2.actors.buffs.LockedFloor;
import com.zrp200.rkpd2.actors.hero.Hero;
import com.zrp200.rkpd2.effects.Beam;
import com.zrp200.rkpd2.effects.CellEmitter;
import com.zrp200.rkpd2.effects.Pushing;
import com.zrp200.rkpd2.effects.Speck;
import com.zrp200.rkpd2.effects.particles.ElmoParticle;
import com.zrp200.rkpd2.effects.particles.ShadowParticle;
import com.zrp200.rkpd2.effects.particles.SparkParticle;
import com.zrp200.rkpd2.items.Heap;
import com.zrp200.rkpd2.items.Item;
import com.zrp200.rkpd2.items.KingsCrown;
import com.zrp200.rkpd2.items.armor.glyphs.Viscosity;
import com.zrp200.rkpd2.items.artifacts.DriedRose;
import com.zrp200.rkpd2.items.artifacts.LloydsBeacon;
import com.zrp200.rkpd2.items.scrolls.ScrollOfTeleportation;
import com.zrp200.rkpd2.items.weapon.enchantments.Grim;
import com.zrp200.rkpd2.levels.CityBossLevel;
import com.zrp200.rkpd2.mechanics.Ballistica;
import com.zrp200.rkpd2.messages.Messages;
import com.zrp200.rkpd2.scenes.GameScene;
import com.zrp200.rkpd2.sprites.CharSprite;
import com.zrp200.rkpd2.sprites.KingSprite;
import com.zrp200.rkpd2.ui.BossHealthBar;
import com.zrp200.rkpd2.ui.BuffIndicator;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.particles.Emitter;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.HashSet;

import static com.zrp200.rkpd2.Assets.Sounds.CHALLENGE;
import static com.zrp200.rkpd2.actors.hero.HeroClass.RAT_KING;

public class DwarfKing extends Mob implements Hero.DeathCommentator {
	@Override public void sayHeroKilled() {
		if(Dungeon.hero.heroClass == RAT_KING) yell("I am truly the superior king...");
		else yell("Let Rat King take this as a lesson...");
		Sample.INSTANCE.play(CHALLENGE);
	}

	{
		spriteClass = KingSprite.class;

		HP = HT = Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 450 : 300;
		EXP = 40;
		defenseSkill = 22;

		properties.add(Property.BOSS);
		properties.add(Property.UNDEAD);

		WANDERING = new Wandering() {
			@Override
			public boolean act(boolean enemyInFOV, boolean justAlerted) {
				if(enemyInFOV && phase == 0) {
					return noticeEnemy();
				}
				return super.act(enemyInFOV, justAlerted);
			}

			@Override
			protected boolean continueWandering() {
				if(phase == 0) {
					spend(TICK);
					return true;
				}
				return super.continueWandering();
			}
		};
	}

	private static int getPhaseHP() {
		return Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 100 : 50;
	}

	@Override
	public int damageRoll() {
		return Random.NormalIntRange( 15, 25 );
	}

	@Override
	public int attackSkill( Char target ) {
		return 26;
	}

	@Override
	public int drRoll() {
		return Random.NormalIntRange(0, 10);
	}

	private int phase = 0; // phase 0 is when he hasn't actually reacted yet.
	private int summonsMade = 0;

	private float summonCooldown = 0;
	private float abilityCooldown = 0;
	private final int MIN_COOLDOWN = Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 8 : 10;
	private final int MAX_COOLDOWN = Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 10 : 14;

	private boolean abilityUsed;

	private int lastAbility = 0;
	private static final int NONE = 0;
	private static final int LINK = 1;
	private static final int TELE = 2;

	private static final String PHASE = "phase";
	private static final String SUMMONS_MADE = "summons_made";

	private static final String SUMMON_CD = "summon_cd";
	private static final String ABILITY_CD = "ability_cd";
	private static final String LAST_ABILITY = "last_ability";

	@Override
	public void storeInBundle(Bundle bundle) {
		super.storeInBundle(bundle);
		bundle.put( PHASE, phase );
		bundle.put( SUMMONS_MADE, summonsMade );
		bundle.put( SUMMON_CD, summonCooldown );
		bundle.put( ABILITY_CD, abilityCooldown );
		bundle.put( LAST_ABILITY, lastAbility );

		bundle.put( "yell", yellSpecialNotice);
		bundle.put( "abilityUsed", abilityUsed );
		bundle.put( "strong", yellStrong );
		bundle.put( "golemSpawned", golemSpawned );
	}

	@Override
	public void restoreFromBundle(Bundle bundle) {
		super.restoreFromBundle(bundle);
		phase = bundle.getInt( PHASE );
		summonsMade = bundle.getInt( SUMMONS_MADE );
		summonCooldown = bundle.getFloat( SUMMON_CD );
		abilityCooldown = bundle.getFloat( ABILITY_CD );
		lastAbility = bundle.getInt( LAST_ABILITY );

		abilityUsed = bundle.getBoolean("abilityUsed");
		yellSpecialNotice = bundle.getBoolean("yell");
		yellStrong = bundle.contains("strong")
				? bundle.getBoolean("strong")
				: Dungeon.hero.heroClass == RAT_KING && phase == 1 && summonsMade < 5;
		golemSpawned = bundle.getBoolean("golemSpawned");
		if (phase == 2) properties.add(Property.IMMOVABLE);
	}
	// for dialogues when he shows a new power.
	private boolean yellSpecialNotice, yellStrong, golemSpawned;

	@Override
	protected boolean act() {
		if(state == HUNTING && yellSpecialNotice && paralysed == 0) { // takes him a hot second to realize who he's fighting.
			yell(Messages.get(this, "notice_" + (Dungeon.hero.heroClass == RAT_KING ? "ratking" : "default")));
			yellSpecialNotice = false;
			Sample.INSTANCE.play(CHALLENGE);
		}
		if (phase == 1) {

			if (summonCooldown <= 0 && summonSubject(Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 2 : 3)){
				summonsMade++;
				summonCooldown += Random.NormalIntRange(MIN_COOLDOWN, MAX_COOLDOWN);
			} else if (summonCooldown > 0){
				summonCooldown--;
			}

			if (paralysed > 0){
				spend(TICK);
				return true;
			}

			if (abilityCooldown <= 0){

				if (lastAbility == NONE) {
					//50/50 either ability
					lastAbility = Random.Int(2) == 0 ? LINK : TELE;
				} else if (lastAbility == LINK) {
					//more likely to use tele
					lastAbility = Random.Int(8) == 0 ? LINK : TELE;
				} else {
					//more likely to use link
					lastAbility = Random.Int(8) != 0 ? LINK : TELE;
				}

				if (lastAbility == LINK && lifeLinkSubject()){
					abilityCooldown += Random.NormalIntRange(MIN_COOLDOWN, MAX_COOLDOWN);
					spend(TICK);
					return true;
				} else if (teleportSubject()) {
					lastAbility = TELE;
					abilityCooldown += Random.NormalIntRange(MIN_COOLDOWN, MAX_COOLDOWN);
					spend(TICK);
					return true;
				}

			} else {
				abilityCooldown--;
			}

		} else if (phase == 2){

			if (Dungeon.isChallenged(Challenges.STRONGER_BOSSES)){
				//challenge logic
				if (summonsMade < 6){
					if (summonsMade == 0) {
						sprite.centerEmitter().start(Speck.factory(Speck.SCREAM), 0.4f, 2);
						Sample.INSTANCE.play(CHALLENGE);
						yell(Messages.get(this, "wave_1"));
					}
					summonSubject(3, DKGhoul.class);
					summonSubject(3, DKGhoul.class);
					spend(3 * TICK);
					summonsMade += 2;
					return true;
				} else if (shielding() <= 300 && summonsMade < 12){
					if (summonsMade == 6) {
						sprite.centerEmitter().start(Speck.factory(Speck.SCREAM), 0.4f, 2);
						Sample.INSTANCE.play(CHALLENGE);
						yell(Messages.get(this, "wave_2"));
					}
					summonSubject(3, DKGhoul.class);
					summonSubject(3, DKGhoul.class);
					if (summonsMade == 6) {
						summonSubject(3, DKMonk.class);
					} else {
						summonSubject(3, DKWarlock.class);
					}
					summonsMade += 3;
					spend(3*TICK);
					return true;
				} else if (shielding() <= 150 && summonsMade < 18) {
					if (summonsMade == 12) {
						sprite.centerEmitter().start(Speck.factory(Speck.SCREAM), 0.4f, 2);
						Sample.INSTANCE.play(CHALLENGE);
						yell(Messages.get(this, "wave_3"));
						summonSubject(3, DKWarlock.class);
						summonSubject(3, DKMonk.class);
						summonSubject(3, DKGhoul.class);
						summonSubject(3, DKGhoul.class);
						summonsMade += 4;
						spend(3*TICK);
					} else {
						summonSubject(3, DKGolem.class);
						summonSubject(3, DKGolem.class);
						summonsMade += 2;
						spend(TICK);
					}
					return true;
				} else {
					spend(TICK);
					return true;
				}
			} else {
				//non-challenge logic
				if (summonsMade < 4) {
					if (summonsMade == 0) {
						sprite.centerEmitter().start(Speck.factory(Speck.SCREAM), 0.4f, 2);
						Sample.INSTANCE.play(CHALLENGE);
						yell(Messages.get(this, "wave_1"));
					}
					summonSubject(3, DKGhoul.class);
					spend(2 * TICK); // extra two turns before summoning next one.
					summonsMade++;
				} else if (shielding() <= 200 && summonsMade < 8) {
					if (summonsMade == 4) {
						sprite.centerEmitter().start(Speck.factory(Speck.SCREAM), 0.4f, 2);
						Sample.INSTANCE.play(CHALLENGE);
						yell(Messages.get(this, "wave_2"));
					}
					if (summonsMade == 7) {
						summonSubject(3, Random.Int(2) == 0 ? DKMonk.class : DKWarlock.class);
					} else {
						summonSubject(3, DKGhoul.class);
					}
					summonsMade++;
				} else if (shielding() <= 100 && summonsMade < 12) {
					sprite.centerEmitter().start(Speck.factory(Speck.SCREAM), 0.4f, 2);
					Sample.INSTANCE.play(CHALLENGE);
					yell(Messages.get(this, "wave_3"));
					summonSubject(4, DKWarlock.class);
					summonSubject(4, DKMonk.class);
					summonSubject(4, DKGhoul.class);
					summonSubject(4, DKGhoul.class);
					summonsMade = 12;
				}
				spend(TICK);
				updateAlert(); // get rid of those stupid alert things.
				return true;
			}
		} else if (phase == 3 && buffs(Summoning.class).size() < 4){
			if (summonSubject(Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 2 : 3)) summonsMade++;
		}

		return super.act();
	}

	private boolean summonSubject( int delay ){
		if (Dungeon.isChallenged(Challenges.STRONGER_BOSSES)) {
			//every 3rd summon is always a monk or warlock, otherwise ghoul
			//except every 9th summon, which is a golem!
			if (summonsMade % 3 == 2) {
				if (summonsMade % 9 == 8){
					return summonSubject(delay, DKGolem.class);
				} else {
					return summonSubject(delay, Random.Int(2) == 0 ? DKMonk.class : DKWarlock.class);
				}
			} else {
				return summonSubject(delay, DKGhoul.class);
			}

		} else {
			//every 4th summon is always a monk or warlock, otherwise ghoul
			if (summonsMade % 4 == 3) {
				return summonSubject(delay, Random.Int(2) == 0 ? DKMonk.class : DKWarlock.class);
			} else {
				return summonSubject(delay, DKGhoul.class);
			}
		}
	}

	private boolean summonSubject( int delay, Class<?extends Mob> type ){
		Summoning s = new Summoning();
		s.pos = ((CityBossLevel)Dungeon.level).getSummoningPos();
		if (s.pos == -1) return false;
		s.summon = type;
		s.delay = delay;
		s.attachTo(this);
		return true;
	}

	private HashSet<Mob> getSubjects(){
		HashSet<Mob> subjects = new HashSet<>();
		for (Mob m : Dungeon.level.mobs){
			if (m.alignment == alignment && m instanceof Subject){
				subjects.add(m);
			}
		}
		return subjects;
	}

	private boolean lifeLinkSubject(){
		Mob furthest = null;

		for (Mob m : getSubjects()){
			boolean alreadyLinked = false;
			for (LifeLink l : m.buffs(LifeLink.class)){
				if (l.object == id()) alreadyLinked = true;
			}
			if (!alreadyLinked) {
				if (furthest == null || Dungeon.level.distance(pos, furthest.pos) < Dungeon.level.distance(pos, m.pos)){
					furthest = m;
				}
			}
		}

		if (furthest != null) {
			Buff.append(furthest, LifeLink.class, 100f).object = id();
			Buff.append(this, LifeLink.class, 100f).object = furthest.id();
			yell(Messages.get(this, "lifelink_" + Random.IntRange(1, 2)));
			sprite.parent.add(new Beam.HealthRay(sprite.destinationCenter(), furthest.sprite.destinationCenter()));
			return true;

		}
		return false;
	}

	private boolean teleportSubject(){
		if (enemy == null) return false;

		Mob furthest = null;

		for (Mob m : getSubjects()){
			if (furthest == null || Dungeon.level.distance(pos, furthest.pos) < Dungeon.level.distance(pos, m.pos)){
				furthest = m;
			}
		}

		if (furthest != null){

			float bestDist;
			int bestPos = pos;

			Ballistica trajectory = new Ballistica(enemy.pos, pos, Ballistica.STOP_TARGET);
			int targetCell = trajectory.path.get(trajectory.dist+1);
			//if the position opposite the direction of the hero is open, go there
			if (Actor.findChar(targetCell) == null && !Dungeon.level.solid[targetCell]){
				bestPos = targetCell;

			//Otherwise go to the neighbour cell that's open and is furthest
			} else {
				bestDist = Dungeon.level.trueDistance(pos, enemy.pos);

				for (int i : PathFinder.NEIGHBOURS8){
					if (Actor.findChar(pos+i) == null
							&& !Dungeon.level.solid[pos+i]
							&& Dungeon.level.trueDistance(pos+i, enemy.pos) > bestDist){
						bestPos = pos+i;
						bestDist = Dungeon.level.trueDistance(pos+i, enemy.pos);
					}
				}
			}

			Actor.add(new Pushing(this, pos, bestPos));
			pos = bestPos;

			//find closest cell that's adjacent to enemy, place subject there
			bestDist = Dungeon.level.trueDistance(enemy.pos, pos);
			bestPos = enemy.pos;
			for (int i : PathFinder.NEIGHBOURS8){
				if (Actor.findChar(enemy.pos+i) == null
						&& !Dungeon.level.solid[enemy.pos+i]
						&& Dungeon.level.trueDistance(enemy.pos+i, pos) < bestDist){
					bestPos = enemy.pos+i;
					bestDist = Dungeon.level.trueDistance(enemy.pos+i, pos);
				}
			}

			if (bestPos != enemy.pos) ScrollOfTeleportation.appear(furthest, bestPos);
			yell(Messages.get(this, "teleport_" + Random.IntRange(1, 2)));
			return true;
		}
		return false;
	}

	@Override
	public void notice() {
		super.notice();
		if (!BossHealthBar.isAssigned()) {
			BossHealthBar.assignBoss(this);
			yell(Messages.get(this, "notice"));
			yellSpecialNotice = true;
			yellStrong = Dungeon.hero.heroClass == RAT_KING;
			for (Char ch : Actor.chars()){
				if (ch instanceof DriedRose.GhostHero){
					((DriedRose.GhostHero) ch).sayBoss();
				}
			}
			if(phase == 0) phase = 1;
		}
		spend(TICK); // there's no good reason he just out and notices you.
	}

	@Override
	public boolean isInvulnerable(Class effect) {
		return phase == 2 && effect != KingDamager.class;
	}

	protected void onDamage(int dmg, Object src) { // handle locked floor
		super.onDamage(dmg, src);
		LockedFloor lock = Dungeon.hero.buff(LockedFloor.class);
		if (lock != null && !isImmune(src.getClass())) lock.addTime(dmg/3);
	}

	@Override
	public void damage(int dmg, Object src) {
		if (isInvulnerable(src.getClass())){
			super.damage(dmg, src);
			return;
		} else if (!isAlive() || dmg < 0) return;
		if(phase == 0) { notice(); }
		if (phase == 3 && !(src instanceof Viscosity.DeferedDamage)) {
			if (dmg >= 0) {
				Viscosity.DeferedDamage deferred = Buff.affect( this, Viscosity.DeferedDamage.class );
				deferred.prolong( dmg );

				sprite.showStatus( CharSprite.WARNING, Messages.get(Viscosity.class, "deferred", dmg) );
			}
			return;
		}
		else if (phase < 2) {
			// yay custom logic
			int preHP = HP;
			dmg = modifyDamage(dmg, src); // determining what our final HP is supposed to be here.
			HP = HP - dmg + shielding();
			// adjust HP to match phase if necessary.
			int HP3 = getPhaseHP();
			// FIXME this is a terrible way of handling it.
			if (HP <= HP3 && !(src instanceof Grim)) { // grim will never actually break the shield.
				HP += HT;
				// TODO is 3f still correct???
				float rawCrit = (HP - HP3)/(HT/3f);
				// this stops it from randomly skipping a phase randomly. the big hits are given more leverage however.
				int criticality = rawCrit <= 2.5 ? Random.round(rawCrit) : 3; // this makes it a bit more lenient.
				if (criticality <= 0) {
					int dmg2 = Math.min(dmg,preHP+250); // everything after the last 50 was deferred.
					dmg = dmg-dmg2;
					HP = preHP; // set HP back to original HP.
					// literally just broke the shield in one go.
					sprite.add(CharSprite.State.SHIELDED);
					onDamage(dmg2, src); // process damage-related stuff
					Sample.INSTANCE.play(Assets.Sounds.SHATTER);
					sprite.remove(CharSprite.State.SHIELDED);
					// enter phase 3 and process the rest of the damage
					HP = HP3;
					enterPhase3(); // skip to phase 3
					yell("...how can this be???");
					if(dmg > 0) damage(dmg, src); // apply deferred damage
				} else {
					// handle normally.
					onDamage(dmg, src); // this is the second part of #damage.
					enterPhase2(criticality);
				}
			}
			else {
				HP = preHP;
				onDamage(dmg,src); // actually damage the guy
				int dmgTaken = preHP-HP;
				abilityCooldown -= dmgTaken / 8f;
				summonCooldown -= dmgTaken / 8f;
			}
		}
		else {
			int preHP = HP;
			super.damage(dmg, src);
			if (phase == 2 && shielding() == 0) { // standard entry into phase 3
				enterPhase3();
				yell(Messages.get(this, "enraged", Dungeon.hero.name()));
			}
			else if (isAlive() && phase == 3 && preHP > 20 && HP < 20) {
				yell(Messages.get(this, "losing"));
			}
		}
	}

	private void enterPhase2 (int wavesLeft) {
		int shielding = wavesLeft * (HT/3);
		HP = 50;
		summonsMade = 4*(3-wavesLeft);
		sprite.showStatus(CharSprite.POSITIVE, Messages.get(this, "invulnerable"));
				ScrollOfTeleportation.appear(this, CityBossLevel.throne);
		properties.add(Property.IMMOVABLE);
		phase = 2;
		sprite.idle();
		Buff.affect(this, DKBarrior.class).setShield(shielding);
		for (Summoning s : buffs(Summoning.class)) {
			s.detach();
		}
		for (Mob m : Dungeon.level.mobs.toArray(new Mob[0])) {
					if (m instanceof Ghoul || m instanceof Monk || m instanceof Warlock || m instanceof Golem) {
				m.die(null);
			}
		}
	}
	private void enterPhase3 () {
			properties.remove(Property.IMMOVABLE);
			phase = 3;
			summonsMade = 1; //monk/warlock on 3rd summon
			sprite.centerEmitter().start( Speck.factory( Speck.SCREAM ), 0.4f, 2 );
			Sample.INSTANCE.play( CHALLENGE );
		}

	@Override
	public boolean isAlive() {
		return super.isAlive() || phase != 3;
	}

	@Override
	public void die(Object cause) {

		GameScene.bossSlain();

		super.die( cause );

		if (Dungeon.level.solid[pos]){
			Heap h = Dungeon.level.heaps.get(pos);
			if (h != null) {
				for (Item i : h.items) {
					Dungeon.level.drop(i, pos + Dungeon.level.width());
				}
				h.destroy();
			}
			Dungeon.level.drop(new KingsCrown(), pos + Dungeon.level.width()).sprite.drop(pos);
		} else {
			Dungeon.level.drop(new KingsCrown(), pos).sprite.drop();
		}

		Badges.validateBossSlain();

		Dungeon.level.unseal();

		for (Mob m : getSubjects()){
			m.die(null);
		}

		LloydsBeacon beacon = Dungeon.hero.belongings.getItem(LloydsBeacon.class);
		if (beacon != null) {
			beacon.upgrade();
		}

		yell( Messages.get(this, "defeated_" + (Random.Int(2)+(Dungeon.hero.heroClass == RAT_KING?0:1)) ) );
	}

	@Override
	public boolean isImmune(Class effect) {
		//immune to damage amplification from doomed in 2nd phase or later, but it can still be applied
		if (phase > 1 && effect == Doom.class && buff(Doom.class) != null ){
			return true;
		}
		return super.isImmune(effect);
	}

	public interface Subject {} // used to identify DK minions

	public static class DKGhoul extends Ghoul implements Subject {
		{
			state = HUNTING;
		}

		@Override
		protected boolean act() {
			partnerID = -2; //no partners
			return super.act();
		}
	}

	public static class DKMonk extends Monk implements Subject {
		{
			state = HUNTING;
		}
	}

	public static class DKWarlock extends Warlock implements Subject {
		{
			state = HUNTING;
		}
	}

	public static class DKGolem extends Golem implements Subject {
		{
			state = HUNTING;
		}
	}

	public static class Summoning extends Buff {

		private int delay;
		private int pos;
		private Class<?extends Mob> summon;

		boolean firstSummon = false;

		private Emitter particles;

		public int getPos() {
			return pos;
		}
		private DwarfKing king() { return (DwarfKing)target; }

		@Override
		public boolean attachTo(Char target) {
			boolean result = super.attachTo(target);
			if(result && !firstSummon) firstSummon = king().phase == 1
					&& king().summonsMade == 0
					&& Dungeon.hero.heroClass == RAT_KING;
			return result;
		}

		@Override
		public boolean act() {
			delay--;

			if (delay <= 0){
				boolean strong = true;
				if (summon == DKGolem.class){
					particles.burst(SparkParticle.FACTORY, 10);
					Sample.INSTANCE.play(Assets.Sounds.CHARGEUP);
				} else if (summon == DKWarlock.class){
					particles.burst(ShadowParticle.CURSE, 10);
					Sample.INSTANCE.play(Assets.Sounds.CURSED);
				} else if (summon == DKMonk.class){
					particles.burst(ElmoParticle.FACTORY, 10);
					Sample.INSTANCE.play(Assets.Sounds.BURNING);
				} else {
					particles.burst(Speck.factory(Speck.BONE), 10);
					Sample.INSTANCE.play(Assets.Sounds.BONES);
					strong = false;
				}
				particles = null;

				if (Actor.findChar(pos) != null){
					ArrayList<Integer> candidates = new ArrayList<>();
					for (int i : PathFinder.NEIGHBOURS8){
						if (Dungeon.level.passable[pos+i] && Actor.findChar(pos+i) == null){
							candidates.add(pos+i);
						}
					}
					if (!candidates.isEmpty()){
						pos = Random.element(candidates);
					}
				}

				DwarfKing king = king();
				if (Actor.findChar(pos) == null) {
					Mob m = Reflection.newInstance(summon);
					m.pos = pos;
					m.maxLvl = -2;
					GameScene.add(m);
					Dungeon.level.occupyCell(m);
					m.state = m.HUNTING;
					if (king.phase == 2){
						Buff.affect(m, KingDamager.class);
					}
					if(firstSummon) {
						king.yell(Messages.get(king(), "first_summon"));
						if(Dungeon.hero.heroClass == RAT_KING) king.yell(Messages.get(king, "summon_rk"));
					}
					if(strong && king.yellStrong) {
						king.yell(Messages.get(king, "strong"));
						king.yellStrong = false;
					}
					if(m instanceof Golem && king.phase < 3 && !king.golemSpawned) {
						king.golemSpawned = true;
						String k = "golem";
						if(Dungeon.hero.heroClass == RAT_KING) k += "_rk";
						king.yell(Messages.get(king, k));
					}
				} else {
					Char ch = Actor.findChar(pos);
					ch.damage(Random.NormalIntRange(20, 40), summon);
					if (king.phase == 2){
						if (Dungeon.isChallenged(Challenges.STRONGER_BOSSES)){
							target.damage(target.HT/18, new KingDamager());
						} else {
							target.damage(target.HT/12, new KingDamager());
						}
					}
					if (!ch.isAlive() && ch == Dungeon.hero) {
						Dungeon.fail(DwarfKing.class);
					}
				}

				detach();
			}

			spend(TICK);
			return true;
		}

		@Override
		public void fx(boolean on) {
			if (on && particles == null) {
				particles = CellEmitter.get(pos);

				if (summon == DKGolem.class){
					particles.pour(SparkParticle.STATIC, 0.05f);
				} else if (summon == DKWarlock.class){
					particles.pour(ShadowParticle.UP, 0.1f);
				} else if (summon == DKMonk.class){
					particles.pour(ElmoParticle.FACTORY, 0.1f);
				} else {
					particles.pour(Speck.factory(Speck.RATTLE), 0.1f);
				}

			} else if (!on && particles != null) {
				particles.on = false;
			}
		}

		private static final String DELAY = "delay";
		private static final String POS = "pos";
		private static final String SUMMON = "summon";

		@Override
		public void storeInBundle(Bundle bundle) {
			super.storeInBundle(bundle);
			bundle.put(DELAY, delay);
			bundle.put(POS, pos);
			bundle.put(SUMMON, summon);

			bundle.put("first", firstSummon);
		}

		@Override
		public void restoreFromBundle(Bundle bundle) {
			super.restoreFromBundle(bundle);
			delay = bundle.getInt(DELAY);
			pos = bundle.getInt(POS);
			summon = bundle.getClass(SUMMON);

			firstSummon = bundle.contains("first") && bundle.getBoolean("first");
		}
	}

	public static class KingDamager extends Buff {

		@Override
		public boolean act() {
			if (target.alignment != Alignment.ENEMY){
				detach();
			}
			spend( TICK );
			return true;
		}

		@Override
		public void detach() {
			super.detach();
			for (Mob m : Dungeon.level.mobs){
				if (m instanceof DwarfKing){
					int damage = m.HT / (Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 18 : 12);
					m.damage(damage, this);
				}
			}
		}
	}

	public static class DKBarrior extends Barrier{

		@Override
		public boolean act() {
			incShield();
			return super.act();
		}

		@Override
		public int icon() {
			return BuffIndicator.NONE;
		}
	}

}
