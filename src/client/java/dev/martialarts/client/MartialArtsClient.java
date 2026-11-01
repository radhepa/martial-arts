package dev.martialarts.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.martialarts.Chain;
import dev.martialarts.MartialArts;
import dev.martialarts.Move;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;

/**
 * Martial Arts' client: with an empty main hand the attack button punches (blocks still mine) and the Kick key (V,
 * rebindable in Options > Controls) kicks; each press goes to the server. Every player's move is posed on its model,
 * and the combo count shows on the HUD.
 * shortcut: in first person a punch shows vanilla's arm swing and a kick shows nothing, until first-person poses.
 */
public final class MartialArtsClient implements ClientModInitializer {
    static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MartialArts.id("martial_arts"));
    public static final KeyMapping KICK = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.martial_arts.kick", InputConstants.KEY_V, CATEGORY));
    /** The move an entity plays and how many ticks in. */
    public static final RenderStateDataKey<String> MOVE = RenderStateDataKey.create(() -> "martial_arts:move");
    public static final RenderStateDataKey<Float> AGE = RenderStateDataKey.create(() -> "martial_arts:age");
    /** How long the combo count stays on screen after the move that last counted started. */
    static final int HUD_TICKS = 40;

    // Each move's peak pose in degrees (x, y, z) for the parts it moves: head, body, right arm, left arm, right leg, left leg.
    private static final Map<String, float[][]> POSES = Map.ofEntries(
            Map.entry("jab", pose(3, -90, 5, 0)),
            Map.entry("cross", pose(2, -90, -5, 0)),
            Map.entry("hook", pose(3, -85, -45, -10)),
            Map.entry("uppercut", pose(2, -160, 0, 10)),
            Map.entry("front_kick", pose(4, -90, 0, 0)),
            Map.entry("low_kick", pose(4, -35, 0, 25)),
            Map.entry("roundhouse", pose(4, -75, 0, 55)),
            Map.entry("spin_kick", pose(5, -85, 0, -60)),
            Map.entry("side_kick", pose(4, -60, 0, 70)),
            Map.entry("knee", pose(4, -115, 0, 0)),
            Map.entry("air_jab", pose(3, -90, 5, 0)),
            Map.entry("air_cross", pose(2, -90, -5, 0)),
            Map.entry("air_hook", pose(3, -85, -45, -10)),
            Map.entry("axe_kick", pose(4, -130, 0, 0)));

    private static float[][] pose(int part, float x, float y, float z) { return new float[][]{{part, x, y, z}}; }

    /** Blends the parts the state's move names towards its peak: in over the startup, held while active, out over the recovery. */
    public static void pose(HumanoidModel<?> m, HumanoidRenderState state) {
        var move = Move.byId(state.getDataOrDefault(MOVE, ""));
        if (move == null) return;
        float t = state.getDataOrDefault(AGE, 0F);
        if (t < 0 || t > move.total()) return;
        float a = t < move.startup() ? (float) Math.sqrt(t / move.startup())
                : t < move.startup() + move.active() ? 1 : 1 - (t - move.startup() - move.active()) / move.recovery();
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        for (float[] p : POSES.getOrDefault(move.id(), new float[0][])) {
            var part = parts[(int) p[0]];
            part.xRot += (float) Math.toRadians(p[1]) * a - part.xRot * a;
            part.yRot += (float) Math.toRadians(p[2]) * a - part.yRot * a;
            part.zRot += (float) Math.toRadians(p[3]) * a - part.zRot * a;
        }
    }

    @Override
    public void onInitializeClient() {
        // At the start of the tick, before vanilla reads the attack button.
        ClientTickEvents.START_CLIENT_TICK.register(MartialArtsClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MartialArts.id("combo"), MartialArtsClient::hud);
    }

    private static void send(Move.Input in) { ClientPlayNetworking.send(new MartialArts.Press(in.ordinal())); }

    private static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null || !ClientPlayNetworking.canSend(MartialArts.Press.TYPE)) return;
        boolean free = p.getMainHandItem().isEmpty() && !p.isSpectator();
        // Punches take the attack button only at the air or a living thing: blocks still mine, boats and frames still break.
        var hit = mc.hitResult;
        boolean punchable = hit == null || hit.getType() == HitResult.Type.MISS
                || hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity;
        if (free && punchable) while (mc.options.keyAttack.consumeClick()) {
            send(Move.Input.PUNCH);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        }
        while (KICK.consumeClick()) if (free) send(Move.Input.KICK);
    }

    /** The combo's length beside the crosshair while it lasts. */
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var s = mc.player.getAttached(MartialArts.SHOWN);
        if (s == null || s.hits() < 2 || mc.level.getGameTime() - s.since() > Math.max(HUD_TICKS, Chain.COMBO_GAP)) return;
        int x = g.guiWidth() / 2 + 14, y = g.guiHeight() / 2 - 4;
        g.text(mc.font, Component.translatable("martial_arts.hud.hits", s.hits()), x, y, s.hits() >= 8 ? 0xFFFF5040 : s.hits() >= 4 ? 0xFFFFC040 : 0xFFFFFFFF);
    }
}
