package me.mars;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.ui.fragments.BlockConfigFragment;
import mindustry.world.blocks.distribution.BufferedItemBridge.BufferedItemBridgeBuild;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;
import mindustry.world.blocks.distribution.MassDriver.MassDriverBuild;
import mindustry.world.blocks.liquid.LiquidBridge.LiquidBridgeBuild;
import mindustry.world.blocks.payloads.PayloadMassDriver.PayloadDriverBuild;

import static mindustry.Vars.control;

public class ConfigHandler {
//	static ObjectMap<Class<? extends Building>, Func2<Building, Building, Boolean>> checkers = new ObjectMap<>();
	static ObjectSet<Class<? extends Building>> validBuilds = ObjectSet.with(
			BufferedItemBridgeBuild.class, ItemBridgeBuild.class, LiquidBridgeBuild.class, MassDriverBuild.class, PayloadDriverBuild.class);

	static Building configClicked;
	private static BlockConfigFragment configFragment;

	public static void init() {
		// Jank time
		configFragment = new ModifiedConfigFragment();
		Reflect.set(InputHandler.class, control.input, "config", configFragment);
		Events.on(EventType.ClientLoadEvent.class, clientLoadEvent -> {
			Timer.schedule(() -> {
				if (control.input.config != configFragment) {
					Log.warn("ConfigFragment was not set. Some mod replacing something perhaps?");
				}
				Log.info("Current InputHandler: @\n ConfigFragment: @", control.input, control.input.config);
			}, 10f);
		});


		Events.run(Trigger.uiDrawBegin, () -> {
			if (configClicked != null) {
				Vec2 mouseCoords = Core.input.mouseWorld();
				if (Core.input.keyDown(KeyCode.mouseLeft)) {
					Draw.z(Layer.overlayUI);
					Draw.color(Pal.accent);
					Lines.line(mouseCoords.x, mouseCoords.y, configClicked.x, configClicked.y);
				} else {
	//						Log.info("Connecting @,@ to @,@", configClicked.tileX(), configClicked.tileY(), mouseX, mouseY);
					Building hovered = Vars.world.buildWorld(mouseCoords.x, mouseCoords.y);
					if (hovered == configClicked) {
						configClicked = null;
					} else if (hovered == null) {
						configFragment.hideConfig();
					} else {
						configClicked.onConfigureBuildTapped(hovered);
						configFragment.hideConfig();
					}
				}
			}
		});
	}

	static void setBuild(Building build) {
		if (validBuilds.contains(build.getClass())) configClicked = build;
	}
}

class ModifiedConfigFragment extends BlockConfigFragment {
	@Override
	public void showConfig(Building tile) {
		ConfigHandler.setBuild(tile);
		super.showConfig(tile);
	}

	@Override
	public void forceHide() {
		ConfigHandler.configClicked = null;
		super.forceHide();
	}

	@Override
	public void hideConfig() {
		ConfigHandler.configClicked = null;
		super.hideConfig();
	}
}
