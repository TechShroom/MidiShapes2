package com.techshroom.unplanned.window;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_STICKY_KEYS;
import static org.lwjgl.glfw.GLFW.GLFW_STICKY_MOUSE_BUTTONS;

import java.nio.ByteBuffer;

import org.lwjgl.Pointer;

/**
 * Window object for a more object oriented GLFW.
 * 
 * @author Kenzie Togami
 */
public interface Window {
	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnCloseCallback {
		/**
		 * Called when {@code window} is closed.
		 * 
		 * @param window
		 *            - window reference
		 */
		public void onWindowClose(Window window);
	}

	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnMoveCallback {
		/**
		 * Called when {@code window} is moved.
		 * 
		 * @param window
		 *            - window reference
		 * @param newX
		 *            - new window X
		 * @param newY
		 *            - new window Y
		 */
		public void onWindowMove(Window window, int newX, int newY);
	}

	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnResizeCallback {
		/**
		 * Called when {@code window} is resized.
		 * 
		 * @param window
		 *            - window reference
		 * @param newWidth
		 *            - new window width
		 * @param newHeight
		 *            - new window height
		 */
		public void onWindowResize(Window window, int newWidth, int newHeight);
	}

	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnResizeFramebufferCallback {
		/**
		 * Called when {@code window}'s framebuffer is resized.
		 * 
		 * @param window
		 *            - window reference
		 * @param newWidth
		 *            - new framebuffer width
		 * @param newHeight
		 *            - new framebuffer height
		 */
		public void onWindowFramebufferResize(Window window, int newWidth,
				int newHeight);
	}

	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnFocusCallback {
		/**
		 * Called when {@code window}'s focus status changes.
		 * 
		 * @param window
		 *            - window reference
		 * @param focused
		 *            - The window's focus status
		 */
		public void onWindowFocusChange(Window window, boolean focused);
	}

	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnMinimizeChangeCallback {
		/**
		 * Called when {@code window}'s minimize status changes.
		 * 
		 * @param window
		 *            - window reference
		 * @param minimized
		 *            - The window's minimize status
		 */
		public void onWindowMinimizeChange(Window window, boolean minimized);
	}

	/**
	 * Window callback interface.
	 * 
	 * @author Kenzie Togami
	 */
	static interface OnRefreshRequestedCallback {
		/**
		 * Called when {@code window} needs to be refreshed.
		 * 
		 * @param window
		 *            - window reference
		 */
		public void onWindowRefreshRequested(Window window);
	}

	static enum InputMode {
		CURSOR(GLFW_CURSOR), STICKY_KEYS(GLFW_STICKY_KEYS), STICKY_MOUSE_BUTTONS(
				GLFW_STICKY_MOUSE_BUTTONS);

		private final int glfwMode;

		private InputMode(int glfwMode) {
			this.glfwMode = glfwMode;
		}

		public int asGLFWConstant() {
			return this.glfwMode;
		}

		@Override
		public String toString() {
			return "GLFW_" + name();
		}
	}

	int getWidth();

	int getHeight();

	int getFramebufferWidth();

	int getFramebufferHeight();

	int getX();

	int getY();

	int getCursorX();

	int getCursorY();

	int getInputMode(InputMode mode);

	ByteBuffer getGammaRamp();

	String getTitle();

	// TODO: return monitor interface
	Object getMonitor();

	String getClipboardContents();

	boolean isContextCurrent();

	boolean isVsyncOn();

	boolean isCloseRequested();

	boolean isVisible();

	/**
	 * Gets an attribute value.
	 * 
	 * @param attr
	 *            - The attribute to get
	 * @return The attribute value
	 * @deprecated Use only for unsupported attribute access
	 */
	@Deprecated
	int getAttribute(int attr);

	/**
	 * Please be careful with this window pointer. Certain methods are only kept
	 * up-to-date via the provider setters in this class and will become
	 * out-of-date if the values are changed elsewhere.
	 * 
	 * <p>
	 * It is recommended to only use this pointer for unsupported features.
	 * </p>
	 * 
	 * @return a {@link Pointer} wrapper for the window pointer
	 */
	Pointer getWindowPointer();

	void setSize(int width, int height);

	void setVsyncOn(boolean on);

	void setCloseRequested(boolean requested);

	void setMinimized(boolean minimized);

	void setVisible(boolean visible);

	void setTitle(String title);

	void destroy();

	void swapBuffers();

	void processEvents();

	void waitForEvents();

	/**
	 * Add a callback for when the Window closes.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onClose(OnCloseCallback callback);

	/**
	 * Add a callback for when the Window moves.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onMove(OnMoveCallback callback);

	/**
	 * Add a callback for when the Window resizes.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onResize(OnResizeCallback callback);

	/**
	 * Add a callback for when the Window framebuffer resizes.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onResizeFramebuffer(OnResizeFramebufferCallback callback);

	/**
	 * Add a callback for when the Window's focus status changes.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onFocusChange(OnFocusCallback callback);

	/**
	 * Add a callback for when the Window's minimize status changes.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onMinimizeChange(OnMinimizeChangeCallback callback);

	/**
	 * Add a callback for when the Window is told to refresh.
	 * 
	 * @param callback
	 *            - the callback
	 */
	void onRefreshRequested(OnRefreshRequestedCallback callback);
}
