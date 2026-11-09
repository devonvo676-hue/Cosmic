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
    enum Anim { NONE, BOB, SWAY_X, SWAY_Y, ARM, FLAP, SPIN, SERPENT, JAW, WALK }

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
        Rig(LivingEntity boss, BossType type, List<Part> parts) { this.boss = boss; this.type = type; this.parts = parts; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Rig> rigs = new HashMap<>();
    private long tick = 0;

    public BossRig(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private static Part p(String model, int parent, float ox, float oy, float oz, float scale, Anim anim, float amp, float freq, float phase, float bias) {
        return new Part(model, parent, ox, oy, oz, scale, anim, amp, freq, phase, bias);
    }

    /** The rig for each boss. k = size of one model pixel in blocks at that part's scale. */
    static List<Part> design(BossType type) {
        List<Part> l = new ArrayList<>();
        switch (type) {
            case VOID_EMPEROR -> {
                float s = 2.2f, k = s / 16, arm = s * 1.2134f;
                l.add(p("void_emperor_robe", -1, 0, 2.0f, 0, s, Anim.SWAY_X, 0.06f, 0.05f, 0, 0));
                l.add(p("void_emperor_torso", -1, 0, 2.0f, 0, s, Anim.BOB, 0.12f, 0.06f, 0, 0));
                l.add(p("void_emperor_head", 1, 0, 11 * k, 0, s, Anim.SWAY_Y, 0.3f, 0.03f, 0, 0));
                l.add(p("void_emperor_arm_r", 1, -7.5f * k, 10.5f * k, 0, arm, Anim.ARM, 0.35f, 0.07f, 0, -0.25f));
                l.add(p("void_emperor_arm_l", 1, 7.5f * k, 10.5f * k, 0, arm, Anim.ARM, 0.35f, 0.07f, 3.14f, 0.25f));
                l.add(p("void_emperor_arm_r", 1, -5.8f * k, 4 * k, 1.5f * k, arm * 0.6f, Anim.ARM, 0.5f, 0.11f, 1f, -0.45f));
                l.add(p("void_emperor_arm_l", 1, 5.8f * k, 4 * k, 1.5f * k, arm * 0.6f, Anim.ARM, 0.5f, 0.11f, 4.1f, 0.45f));
            }
            case SOLAR_TITAN -> {
                float s = 2.6f, k = s / 16;
                l.add(p("solar_titan_body", -1, 0, 2.6f, 0, s, Anim.BOB, 0.15f, 0.05f, 0, 0));
                l.add(p("solar_titan_head", 0, 0, 9 * k, 0, s, Anim.SWAY_Y, 0.35f, 0.03f, 0, 0));
                l.add(p("solar_titan_fist_r", 0, -12 * k, -1 * k, 3 * k, s, Anim.BOB, 0.25f, 0.08f, 0, 0));
                l.add(p("solar_titan_fist_l", 0, 12 * k, -1 * k, 3 * k, s, Anim.BOB, 0.25f, 0.08f, 3.14f, 0));
                l.add(p("solar_titan_ring", 0, 0, 0, 0, s * 1.1f, Anim.SPIN, 0.35f, 0.05f, 0, 0));
            }
            case FROST_LEVIATHAN -> {
                float s = 2.4f, k = s / 16;
                l.add(p("frost_leviathan_coil", -1, 0, 0, 0, s, Anim.NONE, 0, 0, 0, 0));
                l.add(p("frost_leviathan_seg", -1, 0, 5 * k, 2 * k, s, Anim.SERPENT, 0.16f, 0.05f, 0, 0));
                l.add(p("frost_leviathan_seg", 1, 0, 8 * k, 0, s * 0.92f, Anim.SERPENT, 0.18f, 0.05f, 0.9f, 0));
                l.add(p("frost_leviathan_seg", 2, 0, 7.4f * k, 0, s * 0.85f, Anim.SERPENT, 0.2f, 0.05f, 1.8f, 0));
                l.add(p("frost_leviathan_head", 3, 0, 6.8f * k, 0, s, Anim.SERPENT, 0.15f, 0.05f, 3.6f, 0.15f));
                l.add(p("frost_leviathan_jaw", 4, 0, 2 * k, 4 * k, s, Anim.JAW, 0.45f, 0.08f, 0, 0));
            }
            case NEBULA_SERPENT -> {
                float s = 2.0f, k = s / 16;
                l.add(p("nebula_serpent_body", -1, 0, 1.4f, 0, s, Anim.BOB, 0.08f, 0.06f, 0, 0));
                l.add(p("nebula_serpent_head", 0, 0, 3 * k, 8 * k, s, Anim.SWAY_Y, 0.2f, 0.04f, 0, 0));
                l.add(p("nebula_serpent_wing_l", 0, 5 * k, 4 * k, 2 * k, s * 1.5f, Anim.FLAP, 0.55f, 0.12f, 0, 0.15f));
                l.add(p("nebula_serpent_wing_r", 0, -5 * k, 4 * k, 2 * k, s * 1.5f, Anim.FLAP, -0.55f, 0.12f, 0, -0.15f));
                l.add(p("nebula_serpent_tail", 0, 0, 0, -8 * k, s * 1.0042f, Anim.SWAY_Y, 0.3f, 0.05f, 0, 0));
            }
            case COSMIC_DESTROYER -> {
                float s = 2.0f, k = s / 16;
                l.add(p("cosmic_destroyer_body", -1, 0, 1.75f + 3 * k, 0, s, Anim.BOB, 0.04f, 0.1f, 0, 0));
                l.add(p("cosmic_destroyer_head", 0, 0, 1 * k, 10 * k, s, Anim.SWAY_Y, 0.15f, 0.04f, 0, 0));
                l.add(p("cosmic_destroyer_leg_fr", 0, -5 * k, -3 * k, 7 * k, s, Anim.WALK, 0.5f, 0, 0, 0));
                l.add(p("cosmic_destroyer_leg_fl", 0, 5 * k, -3 * k, 7 * k, s, Anim.WALK, 0.5f, 0, 3.14f, 0));
                l.add(p("cosmic_destroyer_leg_br", 0, -5 * k, -3 * k, -7 * k, s, Anim.WALK, 0.5f, 0, 3.14f, 0));
                l.add(p("cosmic_destroyer_leg_bl", 0, 5 * k, -3 * k, -7 * k, s, Anim.WALK, 0.5f, 0, 0, 0));
            }
        }
        return l;
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
        Matrix4f base = new Matrix4f().rotateY(-yaw);
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
            case ARM -> m.rotateZ(p.bias() + p.amp() * 0.3f * s).rotateX(p.amp() * s);
            case FLAP -> m.rotateZ(p.bias() + p.amp() * s);
            case SPIN -> m.rotateX(p.amp()).rotateY((float) (tick * p.freq()));
            case SERPENT -> m.rotateX(p.bias() + p.amp() * s).rotateZ(p.amp() * 0.6f * (float) Math.cos(t));
            case JAW -> m.rotateX(p.amp() * (0.5f + 0.5f * s));
            case WALK -> m.rotateX(p.amp() * (float) Math.sin(rig.walk + p.phase()));
        }
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
