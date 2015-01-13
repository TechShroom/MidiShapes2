package com.techshroom.unplanned.blitter;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_POLYGON;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_QUAD_STRIP;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;

import org.lwjgl.opengl.GL11;

/**
 * Modes for {@link GL11#glBegin(int)}. Below, {@code n} is an integer count
 * starting at one, and {@code N} is the total number of vertices specified.
 * 
 * @author Kenzie Togami
 */
public enum BeginMode {
	/**
	 * Treats each vertex as a single point. Vertex {@code n} defines point
	 * {@code n}. {@code N} points are drawn.
	 */
	POINTS(GL_POINTS),
	/**
	 * Treats each pair of vertices as an independent line segment. Vertices
	 * {@code 2n-1} and {@code 2n} define line {@code n}. {@code N/2} lines are
	 * drawn.
	 */
	LINES(GL_LINES),
	/**
	 * Draws a connected group of line segments from the first vertex to the
	 * last. Vertices {@code n} and {@code n+1} define line {@code n}.
	 * {@code N-1} lines are drawn.
	 */
	LINE_STRIP(GL_LINE_STRIP),
	/**
	 * Draws a connected group of line segments from the first vertex to the
	 * last, then back to the first. Vertices {@code n} and {@code n+1} define
	 * line {@code n}. The last line, however, is defined by vertices {@code N}
	 * and {@code 1}. {@code N} lines are drawn.
	 */
	LINE_LOOP(GL_LINE_LOOP),
	/**
	 * Treats each triplet of vertices as an independent triangle. Vertices
	 * {@code 3n-2}, {@code 3n-1}, and {@code 3n} define triangle {@code n}.
	 * {@code N/3} triangles are drawn.
	 */
	TRIANGLES(GL_TRIANGLES),
	/**
	 * Draws a connected group of triangles. One triangle is defined for each
	 * vertex presented after the first two vertices. For odd {@code n},
	 * vertices {@code n}, {@code n+1}, and {@code n+2} define triangle
	 * {@code n}. For even {@code n}, vertices {@code n+1}, {@code n}, and
	 * {@code n+2} define triangle {@code n}. {@code N-2} triangles are drawn.
	 */
	TRIANGLE_STRIP(GL_TRIANGLE_STRIP),
	/**
	 * Draws a connected group of triangles. One triangle is defined for each
	 * vertex presented after the first two vertices. Vertices {@code 1},
	 * {@code n+1}, and {@code n+2} define triangle {@code n}. {@code N-2}
	 * triangles are drawn.
	 */
	TRIANGLE_FAN(GL_TRIANGLE_FAN),
	/**
	 * Treats each group of four vertices as an independent quadrilateral.
	 * Vertices {@code 4n-3}, {@code 4n-2}, {@code 4n-1}, and {@code 4n} define
	 * quadrilateral {@code n}. {@code N/4} quadrilaterals are drawn.
	 */
	QUADS(GL_QUADS),
	/**
	 * Draws a connected group of quadrilaterals. One quadrilateral is defined
	 * for each pair of vertices presented after the first pair. Vertices
	 * {@code 2n-1}, {@code 2n}, {@code 2n+2}, and {@code 2n+1} define
	 * quadrilateral {@code n}. {@code N/2-1} quadrilaterals are drawn. Note
	 * that the order in which vertices are used to construct a quadrilateral
	 * from strip data is different from that used with independent data.
	 */
	QUAD_STRIP(GL_QUAD_STRIP),
	/**
	 * Draws a single, convex polygon. Vertices {@code 1} through {@code N}
	 * define this polygon.
	 */
	POLYGON(GL_POLYGON);

	private final int glInt;

	private BeginMode(int glInt) {
		this.glInt = glInt;
	}

	/**
	 * Converts this mode to a OpenGL constant value.
	 * 
	 * @return the corresponding {@code int} value
	 */
	public int asGLConstant() {
		return this.glInt;
	}

	@Override
	public String toString() {
		return "GL_" + name();
	}
}
