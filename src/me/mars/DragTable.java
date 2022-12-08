package me.mars;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.ChangeListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.layout.Table;

public class DragTable extends Table {
	float xDelta, yDelta;

	public DragTable() {
		super();

		addListener(new InputListener() {
			float lastX, lastY;

			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				if(Core.app.isMobile() && pointer != 0) return;
				DragTable.this.xDelta += x - this.lastX;
				DragTable.this.yDelta += y - this.lastY;
				x = Mathf.clamp(DragTable.this.x - this.lastX + x, 0, Core.graphics.getWidth()-DragTable.this.width);
				y = Mathf.clamp(DragTable.this.y - this.lastY + y, 0, Core.graphics.getHeight()-DragTable.this.height);
				DragTable.this.setPosition(x, y);
			}

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
				if(Core.app.isMobile() && pointer != 0) return false;
				this.lastX = x;
				this.lastY = y;
				DragTable.this.xDelta = 0;
				DragTable.this.yDelta = 0;
				return true;
			}
		});

		addCaptureListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Element actor) {
				if ((Math.abs(xDelta) > DragTable.this.width || Math.abs(yDelta) > DragTable.this.height)) event.stop();
			}
		});
	}
}
