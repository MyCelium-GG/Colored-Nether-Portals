package my.celium.org.colorednetherportals.client;

import java.lang.reflect.Field;

import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Wraps the game's provider for {@code minecraft:portal} particles: a particle
 * that starts at a colored portal takes that portal's color, with vanilla's
 * random brightness. Everything else (other portals, ender pearls, the
 * dragon egg) stays purple. Installed again whenever a resource reload puts
 * vanilla's provider back. Client only.
 */
final class ColoredPortalParticles implements ParticleProvider<SimpleParticleType> {
    private static Field resourcesField;
    private static boolean broken;

    private final ParticleProvider<SimpleParticleType> vanilla;

    private ColoredPortalParticles(ParticleProvider<SimpleParticleType> vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
            double xd, double yd, double zd, RandomSource random) {
        Particle particle = vanilla.createParticle(type, level, x, y, z, xd, yd, zd, random);
        if (particle instanceof SingleQuadParticle quad && ColoredNetherPortalsMod.isModEnabled()) {
            int rgb = ClientPortals.particleColor(x, y, z);
            if (rgb >= 0) {
                // Vanilla gives each portal particle a random brightness between 0.4 and 1.
                float shade = random.nextFloat() * 0.6f + 0.4f;
                quad.setColor((rgb >> 16 & 0xFF) / 255f * shade, (rgb >> 8 & 0xFF) / 255f * shade,
                        (rgb & 0xFF) / 255f * shade);
            }
        }
        return particle;
    }

    /** Puts the wrapper in place if it isn't (cheap; called every tick). */
    @SuppressWarnings("unchecked")
    static void install(Minecraft minecraft) {
        if (broken || minecraft.particleEngine == null) {
            return;
        }
        try {
            // Vanilla keys providers by registry number; Forge and NeoForge patch it to a map by id.
            Object providers = resources(minecraft.particleEngine).getProviders();
            if (providers instanceof Int2ObjectMap<?> byNumber) {
                Int2ObjectMap<ParticleProvider<?>> map = (Int2ObjectMap<ParticleProvider<?>>) byNumber;
                int key = BuiltInRegistries.PARTICLE_TYPE.getId(ParticleTypes.PORTAL);
                ParticleProvider<?> current = map.get(key);
                if (current != null && !(current instanceof ColoredPortalParticles)) {
                    map.put(key, new ColoredPortalParticles((ParticleProvider<SimpleParticleType>) current));
                }
            } else if (providers instanceof java.util.Map<?, ?> byId) {
                java.util.Map<Object, ParticleProvider<?>> map = (java.util.Map<Object, ParticleProvider<?>>) byId;
                Object key = BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.PORTAL);
                ParticleProvider<?> current = map.get(key);
                if (current != null && !(current instanceof ColoredPortalParticles)) {
                    map.put(key, new ColoredPortalParticles((ParticleProvider<SimpleParticleType>) current));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            ColoredNetherPortalsMod.warn("Can't color portal particles in this Minecraft version: {}", e.toString());
        }
    }

    /** The engine's particle resources, found by type so a renamed field still works. */
    private static ParticleResources resources(ParticleEngine engine) throws ReflectiveOperationException {
        if (resourcesField == null) {
            for (Field field : ParticleEngine.class.getDeclaredFields()) {
                if (field.getType() == ParticleResources.class) {
                    field.setAccessible(true);
                    resourcesField = field;
                    break;
                }
            }
            if (resourcesField == null) {
                throw new NoSuchFieldException("ParticleResources in ParticleEngine");
            }
        }
        return (ParticleResources) resourcesField.get(engine);
    }
}
