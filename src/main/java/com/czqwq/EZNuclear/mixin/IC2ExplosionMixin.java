package com.czqwq.EZNuclear.mixin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.ServerChatEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.EZNuclear.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@Mixin(value = ic2.core.ExplosionIC2.class, remap = false)
public class IC2ExplosionMixin {

    @Unique
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EZNuclear-IC2ExplosionScheduler");
        t.setDaemon(true);
        return t;
    });

    static {
        System.out.println("[EZNuclear] IC2ExplosionMixin loaded");
        FMLCommonHandler.instance()
            .bus()
            .register(new ChatTriggerListener());
    }

    // 原作者逻辑的标志位：允许延迟爆炸通过一次
    @Unique
    private volatile boolean eznuclear_ignoreNext = false;

    // 聊天触发模式的标志位
    @Unique
    private static volatile boolean eznuclear_triggerExplosion = false;

    @Inject(method = "doExplosion", at = @At("HEAD"), cancellable = true)
    private void onDoExplosion(CallbackInfo ci) {
        // 如果禁用 IC2 爆炸，直接取消
        if (!Config.IC2Explosion) {
            ci.cancel();
            return;
        }

        // 聊天触发模式
        if (Config.RequireChatTrigger) {
            ci.cancel();
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();

            // 延迟 5 秒后检查是否触发
            SCHEDULER.schedule(() -> {
                if (eznuclear_triggerExplosion) {
                    try {
                        ((ic2.core.ExplosionIC2) (Object) this).doExplosion();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                } else {
                    if (server != null) {
                        server.getConfigurationManager()
                            .sendChatMsg(new ChatComponentTranslation("§a爆炸已被取消！"));
                    }
                }
                eznuclear_triggerExplosion = false;
            }, 5L, TimeUnit.SECONDS);

            return;
        }

        // ===== 原作者逻辑 =====
        if (eznuclear_ignoreNext) {
            eznuclear_ignoreNext = false;
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return;

        try {
            server.getConfigurationManager()
                .sendChatMsg(new ChatComponentTranslation("info.ezunclear"));
        } catch (Throwable t) {
            t.printStackTrace();
        }

        ci.cancel();

        SCHEDULER.schedule(() -> {
            MinecraftServer s = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (s != null) {
                try {
                    s.getConfigurationManager()
                        .sendChatMsg(new ChatComponentTranslation("info.ezunclear.interact"));
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                eznuclear_ignoreNext = true;

                try {
                    ((ic2.core.ExplosionIC2) (Object) this).doExplosion();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }, 5L, TimeUnit.SECONDS);
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
