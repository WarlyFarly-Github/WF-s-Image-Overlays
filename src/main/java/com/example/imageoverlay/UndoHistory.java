package com.example.imageoverlay;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo for drag mode: moving, resizing and trashing images.
 * The history lives as long as you stay in the current world/server.
 */
public final class UndoHistory {
	private static final int MAX = 50;

	/** The part of an image that dragging can change. */
	public record State(int x, int y, float scale, float stretchY) {
		public static State of(ImageEntry e) {
			return new State(e.x, e.y, e.scale, e.stretchY);
		}

		public void applyTo(ImageEntry e) {
			e.x = x;
			e.y = y;
			e.scale = scale;
			e.stretchY = stretchY;
		}
	}

	private static final class Action {
		final ImageEntry entry;
		final boolean delete;   // true = the image was trashed, false = it was moved/resized
		final State before;
		final State after;      // unused for deletes
		final int index;        // position in the list (deletes only)

		Action(ImageEntry entry, boolean delete, State before, State after, int index) {
			this.entry = entry;
			this.delete = delete;
			this.before = before;
			this.after = after;
			this.index = index;
		}
	}

	private static final Deque<Action> UNDO = new ArrayDeque<>();
	private static final Deque<Action> REDO = new ArrayDeque<>();

	private UndoHistory() {}

	public static boolean canUndo() {
		return !UNDO.isEmpty();
	}

	public static boolean canRedo() {
		return !REDO.isEmpty();
	}

	// ---------------------------------------------------------------- recording

	/** Call after a move/resize finished. {@code before} is the state captured when the drag began. */
	public static void recordEdit(ImageEntry e, State before) {
		State after = State.of(e);
		if (after.equals(before)) return; // nothing actually changed
		push(new Action(e, false, before, after, -1));
	}

	/** Call after an image was trashed (it is already out of the list). */
	public static void recordDelete(ImageEntry e, State before, int index) {
		push(new Action(e, true, before, null, index));
	}

	private static void push(Action a) {
		REDO.clear(); // a new action ends the redo chain (redo entries are never "out of the list")
		UNDO.push(a);
		while (UNDO.size() > MAX) {
			Action old = UNDO.removeLast();
			if (old.delete) old.entry.release(); // can no longer be restored: free its texture
		}
	}

	// ---------------------------------------------------------------- undo / redo

	public static void undo() {
		if (UNDO.isEmpty()) return;
		Action a = UNDO.pop();
		if (a.delete) {
			ImageManager.restore(a.entry, a.index);
		} else {
			a.before.applyTo(a.entry);
		}
		REDO.push(a);
		ImageManager.save();
	}

	public static void redo() {
		if (REDO.isEmpty()) return;
		Action a = REDO.pop();
		if (a.delete) {
			ImageManager.detach(a.entry);
		} else {
			a.after.applyTo(a.entry);
		}
		UNDO.push(a);
		ImageManager.save();
	}

	// ---------------------------------------------------------------- housekeeping

	/** An image was removed some other way (menu "Remove"): its history entries no longer make sense. */
	public static void forget(ImageEntry e) {
		UNDO.removeIf(a -> a.entry == e);
		REDO.removeIf(a -> a.entry == e);
	}

	/** Leaving a world: drop everything and free textures of images that were still waiting in the trash. */
	public static void clear() {
		for (Action a : UNDO) {
			if (a.delete) a.entry.release();
		}
		UNDO.clear();
		REDO.clear();
	}
}
