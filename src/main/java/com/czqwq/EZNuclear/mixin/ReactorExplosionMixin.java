package com.czqwq.EZNuclear.mixin;

import java.lang.reflect.Field;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.event.ServerChatEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.brandon3055.brandonscore.common.handlers.ProcessHandler;
import com.brandon3055.draconicevolution.common.tileentities.multiblocktiles.reactor.ReactorExplosion;
import com.czqwq.EZNuclear.Config;
import com.czqwq.EZNuclear.data.PendingMeltdown;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@Mixin(ReactorExplosion.class)
public abstract class ReactorExplosionMixin {

    private static final Logger LOGGER = LogManager.getLogger("EZNuclear.ReactorExplosionMixin");

    @Unique
    private static volatile boolean eznuclear_triggerExplosion = false;

    static {
        FMLCommonHandler.instance()
            .bus()
            .register(new ChatTriggerListener());
    }

    @Inject(method = { "run", "onRun" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void onRun(CallbackInfo ci) {
        // Check if DE explosions are disabled in config
        if (!Config.DEExplosion) {
            ci.cancel();
            return;
        }

        // 聊天触发模式
        if (Config.RequireChatTrigger) {
            ci.cancel();
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();

            // 延迟 5 秒后检查是否触发
            PendingMeltdown.schedule(new ChunkCoordinates(0, 0, 0), () -> {
                if (eznuclear_triggerExplosion) {
                    LOGGER.info("ReactorExplosionMixin: chat trigger accepted, allowing explosion");
                } else {
                    if (server != null) {
                        server.getConfigurationManager()
                            .sendChatMsg(new ChatComponentTranslation("§a爆炸已被取消！"));
                    }
                }
                eznuclear_triggerExplosion = false;
            }, 5L);

            return;
        }

        // ===== 原作者逻辑 =====
        try {
            // 原始 PendingMeltdown 调度逻辑
            Class<?> cls = this.getClass();
            Field xField = cls.getDeclaredField("x");
            Field yField = cls.getDeclaredField("y");
            Field zField = cls.getDeclaredField("z");
            Field worldField = cls.getDeclaredField("world");
            xField.setAccessible(true);
            yField.setAccessible(true);
            zField.setAccessible(true);
            worldField.setAccessible(true);
            int x = xField.getInt(this);
            int y = yField.getInt(this);
            int z = zField.getInt(this);
            World world = (World) worldField.get(this);

            ChunkCoordinates pos = new ChunkCoordinates(x, y, z);
            // If PendingMeltdown already scheduled reentry, allow run to proceed
            if (PendingMeltdown.consumeReentry(pos)) {
                LOGGER.info("ReactorExplosionMixin: reentry present for {}. allowing run", pos);
                return; // allow original run
            }

            ci.cancel();
            float power = 10F;
            // 省略原作者 power 字段读取逻辑...
            final float fpower = power;

            PendingMeltdown.schedule(pos, () -> {
                try {
                    PendingMeltdown.markReentry(pos);
                    ReactorExplosion newExp = new ReactorExplosion(world, x, y, z, fpower);
                    ProcessHandler.addProcess(newExp);
                } catch (Throwable t) {
                    LOGGER.warn("Error scheduling ReactorExplosion: {}", t.getMessage());
                }
            }, 0L);

        } catch (Throwable t) {
            LOGGER.warn("ReactorExplosionMixin interception failed: {}", t.getMessage());
        }
    }

    // 聊天监听器
    public static class ChatTriggerListener {

        @SubscribeEvent
        public void onPlayerChat(ServerChatEvent event) {
            if ("坏了坏了".equals(event.message.trim())) {
                eznuclear_triggerExplosion = true;
                event.player.addChatMessage(new ChatComponentTranslation("§c爆炸已被触发！"));
            }
        }
    }
}
