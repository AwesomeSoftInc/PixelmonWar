package net.ioixd.pixelmonwar.listener;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

public class HitPokemonListener {

    HashMap<PixelmonEntity, LivingEntity> angerMap = new HashMap<>();
    Vector<ArrowEntity> fireballMap = new Vector<>();

    final Object mutex = new Object();

    int tickTimer1 = 0;
    int tickTimer2 = 0;
    int tickTimer3 = 0;

    // When an entity is hit, if it's a Pokemon and the source was a Player,
    // then we add a pair of the two to a map in memory, which gets referenced below.
    @SubscribeEvent
    public void onPokemonHit(LivingHurtEvent event) {
        LivingEntity target = null;
        try {
            target = (LivingEntity) event.getSource().getEntity();
        } catch (ClassCastException ignored) {
            return;
        }

        try {
            EntityType<?> type = event.getSource().getEntity().getType();
            if(type != EntityType.PLAYER && type != EntityType.ARROW) {
                return;
            }
            if(type == EntityType.ARROW) {
                ArrowEntity arrow = (ArrowEntity) event.getSource().getEntity();
                if(arrow.getOwner().getType() == EntityType.PLAYER) {
                    target = (LivingEntity) arrow.getOwner();
                }
            }
        } catch (NullPointerException ex) {
            return;
        }

        PixelmonEntity pokemon = null;

        try {
            pokemon = (PixelmonEntity) event.getEntity();
        } catch (ClassCastException ignored) {
            return;
        }

        if(!angerMap.containsKey(pokemon)) {
            angerMap.put(pokemon,target);
        }
    }

    @SubscribeEvent
    public void onPokemonDeath(LivingDeathEvent event) {
        LivingEntity target = null;
        try {
            target = (LivingEntity) event.getSource().getEntity();
        } catch (ClassCastException ignored) {
            return;
        }

        PixelmonEntity pokemon = null;

        try {
            pokemon = (PixelmonEntity) event.getEntity();
        } catch (ClassCastException ignored) {
            return;
        }

        angerMap.remove(pokemon);
    }

    // Every 10 ticks we check that map above, and set the ai for each of the pokemon in it.
    @SubscribeEvent
    public void onTickEvent(TickEvent.ClientTickEvent event) {
        tickTimer1++;
        tickTimer2++;
        tickTimer3++;
        // timer for resetting angered pokemon's ai as much as we can
        // because for some reason pixelmon keeps overwriting what we have at random.
        if(tickTimer1 >= 5) {
            for(Map.Entry<PixelmonEntity, LivingEntity> entry : angerMap.entrySet()) {
                try {
                    PixelmonEntity pokemon = entry.getKey();
                    LivingEntity target = entry.getValue();

                    NearestAttackableTargetGoal<?> goal1 = new NearestAttackableTargetGoal<>(pokemon, PlayerEntity.class, true);
                    goal1.setTarget(target);
                    pokemon.goalSelector.getRunningGoals().forEach(pokemon.goalSelector::removeGoal);
                    pokemon.goalSelector.addGoal(0,goal1);
                } catch (NullPointerException ex) {
                    synchronized (mutex) {
                        angerMap.remove(entry);
                    }
                }
            }
            tickTimer1 = 0;
        }
        // timer for making an angered pokemon randomly attack.
        if(tickTimer2 >= 5) {
            for(Map.Entry<PixelmonEntity, LivingEntity> entry : angerMap.entrySet()) {
                try {
                    PixelmonEntity pokemon = entry.getKey();
                    LivingEntity target = entry.getValue();

                    if(!pokemon.isDeadOrDying()) {
                        double velocitySpeed = (pokemon.getSpeed()) * 100;
                        double rand = Math.random();
                        if(rand >= (1.0 - pokemon.getSpeed())) {
                            ArrowEntity arrow = new ArrowEntity(pokemon.level, pokemon);
                            Vector3d delta = pokemon.getDeltaMovement();
                            arrow.setDeltaMovement(delta.x*velocitySpeed, delta.y, delta.z*velocitySpeed);
                            arrow.setSilent(true);
                            arrow.setInvisible(true);
                            pokemon.level.addFreshEntity(arrow);
                            fireballMap.add(arrow);
                        }
                    } else {
                        synchronized (mutex) {
                            angerMap.remove(entry);
                        }
                    }
                } catch (NullPointerException ex) {
                    System.out.println("removing entry");
                    synchronized (mutex) {
                        angerMap.remove(entry);
                    }
                }
            }
            tickTimer2 = 0;
        }
        // timer for particle effects at arrows
        if(tickTimer3 >= 10) {
            for (ArrowEntity arrow : fireballMap) {
                arrow.level.addParticle(ParticleTypes.FLAME, arrow.getX(), arrow.getY(), arrow.getZ(), 1.0d, 1.0d, 1.0d);
            }
            tickTimer3 = 0;
        }
    }
}
