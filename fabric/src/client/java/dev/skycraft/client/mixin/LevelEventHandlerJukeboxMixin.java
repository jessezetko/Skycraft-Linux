// SkyCraft+ : Skyrim music discs
package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import dev.skycraft.craft.SkyMusicDiscs;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A jukebox playing a Skyrim music disc (SkyMusicDiscs) has Skyrim play that song there (Minecraft
 * has no sound of its own here); it stopping (the disc taken out, the jukebox broken, the song
 * over) stops it in Skyrim too. Every jukebox's stop is told: Skyrim ignores one it isn't playing. Play is told
 * after Minecraft's own (it stops the jukebox's old song first; told before, Skyrim's new one stopped at once).
 */
@Mixin(net.minecraft.client.renderer.LevelEventHandler.class)
public abstract class LevelEventHandlerJukeboxMixin {
	@Inject(method = "playJukeboxSong", at = @At("RETURN"))
	private void skycraft$play(Holder<JukeboxSong> song, BlockPos pos, CallbackInfo ci) {
		int disc = song.unwrapKey().map(SkyMusicDiscs::indexOf).orElse(-1);
		if (disc >= 0 && SkyClient.linked() && dev.skycraft.SkySettings.flag("skyrimMusicDiscs", true)) {
			SkyLink.pushEvent(Proto.EV_JUKEBOX, 0, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, 0.0f, disc + 1);
		}
	}

	@Inject(method = "stopJukeboxSong", at = @At("HEAD"))
	private void skycraft$stop(BlockPos pos, CallbackInfo ci) {
		if (SkyClient.linked()) {
			SkyLink.pushEvent(Proto.EV_JUKEBOX, 0, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, 0.0f, 0);
		}
	}
}
