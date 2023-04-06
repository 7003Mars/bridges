package me.mars;

import arc.func.Cons;
import arc.math.geom.Rect;
import arc.util.Log;

public class QuadTree<T extends arc.math.geom.QuadTree.QuadTreeObject> extends arc.math.geom.QuadTree<T> {
	private boolean intersecting = false;
	public QuadTree(Rect bounds) {
		super(bounds);
	}

	@Override
	public void intersect(float x, float y, float width, float height, Cons<T> out) {
		this.intersecting = true;
		super.intersect(x, y, width, height, out);
		this.intersecting = false;
	}

	@Override
	public void insert(T obj) {
		if (this.intersecting) Log.err(new Throwable("Items should not be inserted when intersecting"));
		super.insert(obj);
	}

	@Override
	public boolean remove(T obj) {
		if (this.intersecting) Log.err(new Throwable("Items should not be removed when intersecting"));
		return super.remove(obj);
	}
}
