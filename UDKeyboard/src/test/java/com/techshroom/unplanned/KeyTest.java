package com.techshroom.unplanned;

import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.lwjgl.glfw.GLFW;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.techshroom.unplanned.keyboard.Key;

/**
 * Testing for the {@link Key} class.
 * 
 * @author Kenzie Togami
 */
public class KeyTest {
	/**
	 * Ensure that the translation works backwards from
	 * {@code Key -> GLFW constant}.
	 */
	@Test
	public void ensureKeyNamesExist() {
		List<String> keyNames = grabKeyConstantNames();
		for (String constant : keyNames) {
			try {
				assertNotNull("no such key constant " + constant,
						GLFW.class.getDeclaredField(constant));
			} catch (NoSuchFieldException | SecurityException e) {
				Throwables.propagate(e);
			}
		}
	}

	private List<String> grabKeyConstantNames() {
		ImmutableList.Builder<String> builder = ImmutableList.builder();
		for (Key k : Key.values()) {
			builder.add(k.toString());
		}
		return builder.build();
	}
}
