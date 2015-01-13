package com.techshroom.unplanned.blitter;

import java.io.Closeable;

/**
 * Interface for object oriented OpenGL try-with-resources. Implementors of this
 * interface support being used in a try-with-resources block so that it is
 * impossible to forget to end a OpenGL section.
 * 
 * @author Kenzie Togami
 */
public interface TryWithGL extends Closeable {
	/**
	 * Gets the name for the function that is called when this TWGL is made.
	 * 
	 * @return the name of the start function
	 */
	String getStartFunctionName();

	/**
	 * Gets the name for the function that is called when this TWGL is closed.
	 * 
	 * @return the name of the start function
	 */
	String getEndFunctionName();
}
