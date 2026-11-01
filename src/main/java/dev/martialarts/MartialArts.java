package dev.martialarts;

import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Martial Arts' server side: a client's punch and kick presses feed each player's {@link Chain}; on a move's first
 * active tick every living thing in reach in front of the player is struck. The server owns the chain and the hits; a
 * client only says which button went down.
 * shortcut: hitstun is heavy Slowness and the pose waits for the server's sync, until hitstop and client prediction.
 */
public final class MartialArts implements ModInitializer {
    public static final String MOD_ID = "martial_arts";
    /** How wide the strike cone is: the cosine of its half-angle around the look direction. */
    static final double CONE = .6;
    /** Upward speed an air hit gives the striker, so an air string hangs with its target. */
    static final double HANG = .2;

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    /** The move a player is playing (by id, "" for none), the game tick it started and the combo's length: synced to every client. */
    public record Shown(String move, long since, int hits) {
        public static final StreamCodec<ByteBuf, Shown> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Shown::move,
                ByteBufCodecs.VAR_LONG, Shown::since, ByteBufCodecs.VAR_INT, Shown::hits, Shown::new);
    }
    public static final AttachmentType<Shown> SHOWN = AttachmentRegistry.create(id("shown"), b -> b.syncWith(Shown.CODEC, AttachmentSyncPredicate.all()));

    /** A button the client pressed: a {@link Move.Input} by ordinal. */
    public record Press(int input) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(id("press"));
        public static final StreamCodec<FriendlyByteBuf, Press> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Press::input, Press::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Fired on the server after a strike lands, with the combo's length so far (other mods score or react here). */
    public interface Hit { void hit(ServerPlayer player, LivingEntity target, Move move, int hits); }
    public static final Event<Hit> HIT = EventFactory.createArrayBacked(Hit.class, all -> (player, target, move, hits) -> {
        for (var l : all) l.hit(player, target, move, hits);
    });

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, context) -> press(context.player(), payload.input()));
        ServerTickEvents.END_SERVER_TICK.register(MartialArts::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CHAINS.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> CHAINS.clear());
    }

    private static void press(ServerPlayer p, int input) {
        if (input < 0 || input >= Move.Input.values().length || p.isSpectator() || !p.isAlive() || !p.getMainHandItem().isEmpty()) return;
        CHAINS.computeIfAbsent(p.getUUID(), u -> new Chain()).press(Move.Input.values()[input]);
    }

    private static void tick(MinecraftServer server) {
        for (var p : server.getPlayerList().getPlayers()) {
            var chain = CHAINS.get(p.getUUID());
            if (chain == null) continue;
            var step = chain.tick(!p.onGround());
            long now = p.level().getGameTime();
            if (step.started() != null) start(p, chain, step.started(), now);
            if (step.strike() != null) strike(p, chain, step.strike(), now);
        }
    }

    private static Vec3 flatLook(ServerPlayer p) {
        var look = p.getLookAngle();
        var flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
    }

    private static void start(ServerPlayer p, Chain chain, Move m, long now) {
        var dir = flatLook(p).scale(m.lunge());
        if (m.lunge() > 0) {
            p.setDeltaMovement(p.getDeltaMovement().add(dir));
            p.syncVelocity = true;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, .6F, 1.2F + p.getRandom().nextFloat() * .3F);
        p.setAttached(SHOWN, new Shown(m.id(), now, chain.hits()));
    }

    private static void strike(ServerPlayer p, Chain chain, Move m, long now) {
        var level = p.level();
        var eye = p.getEyePosition();
        var look = p.getLookAngle();
        var away = flatLook(p);
        boolean landed = false;
        for (var e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(m.reach()), e -> e != p && e.isAlive() && !e.isSpectator())) {
            var to = e.getBoundingBox().getCenter().subtract(eye);
            if (Math.sqrt(e.getBoundingBox().distanceToSqr(eye)) > m.reach() || to.normalize().dot(look) < CONE) continue;
            float scale = Chain.scale(chain.hits() + 1);
            e.setInvulnerableTime(0);   // combos land faster than vanilla's half-second of hurt immunity
            if (!e.hurtServer(level, level.damageSources().playerAttack(p), m.damage() * scale)) continue;
            int hits = chain.hit();
            // Replaces vanilla's knockback: each move sets its own, so strings stay in reach and launchers launch.
            e.setDeltaMovement(away.x * m.push(), m.lift() != 0 ? m.lift() : e.getDeltaMovement().y, away.z * m.push());
            e.syncVelocity = true;
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Math.round(m.hitstun() * scale), 6, false, false), p);
            var at = e.getBoundingBox().getCenter();
            level.sendParticles(m.damage() >= 4 ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, .2, .2, .2, .25);
            var sound = m.lift() > .5 || m.lift() < 0 ? SoundEvents.PLAYER_ATTACK_CRIT : m.push() >= .7 ? SoundEvents.PLAYER_ATTACK_KNOCKBACK
                    : m.damage() >= 3 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK;
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1, .9F + Math.min(hits, 10) * .04F);
            HIT.invoker().hit(p, e, m, hits);
            landed = true;
        }
        if (!landed) return;
        if (m.air()) {
            var v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, Math.max(v.y, HANG), v.z);
            p.syncVelocity = true;
        }
        var shown = p.getAttached(SHOWN);
        p.setAttached(SHOWN, new Shown(m.id(), shown == null ? now : shown.since(), chain.hits()));
    }
}
