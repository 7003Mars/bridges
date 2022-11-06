package me.mars;

import arc.Core;
import arc.Events;
import arc.func.Func2;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.ui.fragments.BlockConfigFragment;
import mindustry.world.blocks.distribution.BufferedItemBridge.BufferedItemBridgeBuild;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.distribution.MassDriver.MassDriverBuild;

import java.lang.reflect.Field;

public class ConfigHandler {
	static ObjectMap<Class<? extends Building>, Func2<Building, Building, Boolean>> checkers = new ObjectMap<>();

	static Building configClicked;
	private static BlockConfigFragment configFragment;

	public static void init() {
		// Jank time
		configFragment = new ModifiedConfigFragment();
//		Field field = Vars.control.input.getClass().getDeclaredField("config");

		Reflect.set(InputHandler.class, Vars.control.input, "config", configFragment);


		checkers.put(BufferedItemBridgeBuild.class,
				(b1, b2) -> ((ItemBridge)b1.block).linkValid(b1.tile(), b2.tile(), false));
		checkers.put(MassDriverBuild.class,
				(b1, b2) -> b1.block == b2.block && b1.team == b2.team && ((MassDriver)b1.block).range > b1.dst(b2));

//		Events.run(Trigger.update, () -> {
//
//			if (configFragment.isShown() && configClicked == null) {
//				Building selected = configFragment.getSelected();
//				if (checkers.containsKey(selected.getClass())) configClicked = selected;
//			} else if (!configFragment.isShown()){
//				configClicked = null;
//			}
//
//		});

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
//						configClicked = null;
					} else if (checkers.get(configClicked.getClass()).get(configClicked, hovered)) {
						configClicked.onConfigureBuildTapped(hovered);
//						configClicked = null;
						configFragment.hideConfig();
					}
					// HIde if: Configured or
					// Dont hide if its itself
//					if (hovered != configClicked && checkers.get(configClicked.getClass()).get(configClicked, hovered)) {
//						configClicked.onConfigureBuildTapped(hovered);
//						configClicked = null;
//						configFragment.hideConfig();
//					} else if (hovered != configClicked) {
//						configClicked = null;
//						configFragment.hideConfig();
//					}
				}
			}
		});
	}

	static void setBuild(Building build) {
		if (checkers.containsKey(build.getClass())) configClicked = build;
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
