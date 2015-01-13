package com.techshroom.unplanned.blitter;

import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glEnd;

import org.lwjgl.opengl.GL11;

/**
 * Big entry point for the graphics section of UnplannedDescent.
 * 
 * @author Kenzie Togami
 */
public final class GL {
	private GL() {
		throw new AssertionError();
	}

	/**
	 * Start a new try-with-GL block for {@link GL11#glBegin(int)}.
	 * 
	 * @param mode
	 *            - the mode to begin with
	 * @return the try-with-resources object
	 */
	public static TryWithGL begin(final BeginMode mode) {
		return new TWGLBase("glBegin", "glEnd") {
			@Override
			protected void callStartFunction() {
				glBegin(mode.asGLConstant());
			}

			@Override
			protected void callEndFunction() {
				glEnd();
			}
		};
	}
}
