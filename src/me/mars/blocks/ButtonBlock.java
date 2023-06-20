package me.mars.blocks;

import arc.graphics.g2d.TextureRegion;
import mindustry.world.Block;

public abstract class ButtonBlock extends Block {
	public TextureRegion blockIcon;
	public ButtonBlock(String name, TextureRegion blockIcon) {
		super(name);
		this.blockIcon = blockIcon;
	}

	public abstract void clicked();

	@Override
	public void loadIcon() {
		super.loadIcon();
		this.uiIcon = blockIcon;
		this.fullIcon = blockIcon;
	}
}
