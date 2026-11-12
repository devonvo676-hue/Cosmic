package com.cosmicabilities.boss;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Custom 3D boss models. The real mob is made invisible and a rig of model parts (resource pack item models) is
 * posed on top of it every tick: joints are chained (arms hang off the torso, the head off the neck...) and each
 * joint has its own idle animation - breathing, swaying arms, flapping wings, a spinning ring, walking legs.
 * The parts flash red when the boss is hit.
 */
public class BossRig {
    enum Anim { NONE, BOB, SWAY_X, SWAY_Y, ARM, FLAP, SPIN, SERPENT, JAW, WALK, ROLL }

    /** offsets are in blocks, in the parent joint's frame */
    record Part(String model, int parent, float ox, float oy, float oz, float scale, Anim anim, float amp, float freq, float phase, float bias) {}

    private static final class Rig {
        final LivingEntity boss;
        final BossType type;
        final List<Part> parts;
        final List<ItemDisplay> displays = new ArrayList<>();
        Location last;
        double walk;
        boolean flashing;
        float size = 1f;
        long attackStart = -100;
        Rig(LivingEntity boss, BossType type, List<Part> parts) { this.boss = boss; this.type = type; this.parts = parts; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Rig> rigs = new HashMap<>();
    private long tick = 0;

    public BossRig(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /** The joints for each boss (generated with the models, see BossRigData). */
    static List<Part> design(BossType type) {
        return BossRigData.parts(type);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("bosses.custom-models", true);
    }

    public void attach(LivingEntity boss, BossType type) {
        if (!enabled() || rigs.containsKey(boss.getUniqueId())) return;
        boss.setInvisible(true);
        EntityEquipment eq = boss.getEquipment();
        if (eq != null) eq.clear();
        Rig rig = new Rig(boss, type, design(type));
        rig.size = (float) plugin.bosses().sizeMult();
        Location at = boss.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        for (Part part : rig.parts) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("cosmic", "boss_" + part.model()));
            item.setItemMeta(meta);
            ItemDisplay d = boss.getWorld().spawn(at, ItemDisplay.class, e -> {
                e.setItemStack(item);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setPersistent(false);
                e.addScoreboardTag(Fx.TAG);
                e.addScoreboardTag(Fx.KEEP);
                e.setDisplayWidth(10);
                e.setDisplayHeight(10);
                e.setViewRange(3f);
                e.setTeleportDuration(2);
                e.setGlowColorOverride(Color.fromRGB(0xff2020));
            });
            rig.displays.add(d);
        }
        rig.last = boss.getLocation();
        rigs.put(boss.getUniqueId(), rig);
        pose(rig);
    }

    /** Every tick: follow the boss, pose every joint, flash red when hit. */
    public void tick() {
        tick++;
        for (Iterator<Rig> it = rigs.values().iterator(); it.hasNext(); ) {
            Rig rig = it.next();
            if (!rig.boss.isValid() || rig.boss.isDead()) {
                rig.displays.forEach(d -> { if (d.isValid()) d.remove(); });
                it.remove();
                continue;
            }
            Location now = rig.boss.getLocation();
            if (rig.last != null && rig.last.getWorld().equals(now.getWorld())) {
                double dx = now.getX() - rig.last.getX(), dz = now.getZ() - rig.last.getZ();
                rig.walk += Math.sqrt(dx * dx + dz * dz) * 2.6;
            }
            rig.last = now;
            Location at = now.clone();
            at.setYaw(0);
            at.setPitch(0);
            boolean hurt = rig.boss.getNoDamageTicks() > rig.boss.getMaximumNoDamageTicks() - 6;
            for (ItemDisplay d : rig.displays) {
                if (!d.isValid()) continue;
                if (d.getLocation().distanceSquared(at) > 0.0004) d.teleport(at);
                if (hurt != rig.flashing) d.setGlowing(hurt);
            }
            rig.flashing = hurt;
            pose(rig);
        }
    }

    private void pose(Rig rig) {
        float yaw = (float) Math.toRadians(rig.boss.getBodyYaw());
        Matrix4f base = new Matrix4f().scale(rig.size).rotateY(-yaw);
        Matrix4f[] joints = new Matrix4f[rig.parts.size()];
        for (int i = 0; i < rig.parts.size(); i++) {
            Part part = rig.parts.get(i);
            Matrix4f m = new Matrix4f(part.parent() < 0 ? base : joints[part.parent()]);
            m.translate(part.ox(), part.oy(), part.oz());
            animate(m, part, rig);
            joints[i] = m;
            ItemDisplay d = rig.displays.get(i);
            if (!d.isValid()) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformationMatrix(new Matrix4f(m).scale(part.scale()));
        }
    }

    private void animate(Matrix4f m, Part p, Rig rig) {
        double t = tick * p.freq() + p.phase();
        float s = (float) Math.sin(t);
        switch (p.anim()) {
            case NONE -> { }
            case BOB -> m.translate(0, p.amp() * s, 0);
            case SWAY_X -> m.rotateX(p.amp() * s);
            case SWAY_Y -> m.rotateY(p.amp() * s);
            case ARM -> {
                // special attack: both arms raise up and slam down over ~16 ticks
                long since = tick - rig.attackStart;
                float slam = since < 16 ? (float) (since < 9 ? -2.4 * Math.sin(since / 9.0 * Math.PI / 2) : -2.4 * (1 - (since - 9) / 7.0)) : 0f;
                m.rotateZ(p.bias() + p.amp() * 0.3f * s).rotateX(p.amp() * s + slam);
            }
            case FLAP -> m.rotateZ(p.bias() + p.amp() * s);
            case SPIN -> m.rotateX(p.amp()).rotateY((float) (tick * p.freq()));
            case SERPENT -> m.rotateX(p.bias() + p.amp() * s).rotateZ(p.amp() * 0.6f * (float) Math.cos(t));
            case JAW -> m.rotateX(p.amp() * (0.5f + 0.5f * s));
            case WALK -> m.rotateX(p.amp() * (float) Math.sin(rig.walk + p.phase()));
            case ROLL -> m.rotateZ((float) (tick * p.freq()));
        }
    }

    /** Plays the arm-slam pose on this boss's rig (used for its special attacks). */
    public void attackPose(UUID boss) {
        Rig rig = rigs.get(boss);
        if (rig != null) rig.attackStart = tick;
    }

    public void shutdown() {
        for (Rig rig : rigs.values()) rig.displays.forEach(d -> { if (d.isValid()) d.remove(); });
        rigs.clear();
    }

    public void detach(UUID boss) {
        Rig rig = rigs.remove(boss);
        if (rig != null) rig.displays.forEach(d -> { if (d.isValid()) d.remove(); });
    }

    static Display none() { return null; }
}
