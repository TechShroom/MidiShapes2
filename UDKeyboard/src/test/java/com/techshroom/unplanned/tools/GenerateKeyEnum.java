package com.techshroom.unplanned.tools;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import autovalue.shaded.com.google.common.common.base.Throwables;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.squareup.javawriter.JavaWriter;
import com.techshroom.unplanned.keyboard.Key;

import fj.data.Array;

/**
 * Generator tool for the {@link Key} enum values
 * 
 * @author Kenzie Togami
 */
public final class GenerateKeyEnum {
	private GenerateKeyEnum() {
	}

	/**
	 * Key pattern: start of string, class name (%s), "_KEY_", key name, end of
	 * string.
	 */
	private static final String KEY_NAME_PATTERN = "^%s_KEY_(.+)$";
	/**
	 * Javadoc for the entire class.
	 */
	private static final String KEY_ENUM_JAVADOC = ""
			+ "Key enum values for the {@link GLFW} class." + "\n\n"
			+ "@author Kenzie Togami";
	/**
	 * Specialized javadoc for each constant.
	 */
	private static final String KEY_ENUM_CONSTANT_JAVADOC = ""
			+ "The value corresponding to the key '%s'.";
	private static final String GET_GLFWCODE_JAVADOC = ""
			+ "Gets the corresponding {@code GLFW int code} for the key."
			+ "\n\n"
			+ "@return The corresponding {@ode GLFW int code} for the key";
	private static final ImmutableMap<String, String> KEY_ENUM_CONSTANT_TO_JAVADOC = ImmutableMap
			.<String, String> builder()
			.put("UNKNOWN", "The value corresponding to an unknown key.")
			.build();
	/**
	 * Very limited conversion from key names to field names. Pretty much only
	 * works on numbers.
	 */
	private static final Function<String, String> CONVERT_TO_JAVA_IDENTIFIERS = new Function<String, String>() {
		@Override
		public String apply(String input) {
			String validated = input;
			if (Character.isDigit(input.charAt(0))) {
				validated = "NUM_" + input;
			}
			return validated;
		}
	};

	/**
	 * Very limited conversion from key names to Javadoc. Special Javadocs can
	 * be configured by inserting them into the map.
	 */
	private static String keysToJavadoc(String input) {
		if (KEY_ENUM_CONSTANT_TO_JAVADOC.containsKey(input)) {
			return KEY_ENUM_CONSTANT_TO_JAVADOC.get(input);
		}
		String validated = input;
		if (input.startsWith("NUM_")) {
			validated = input.substring(4).intern();
		} else if (input.startsWith("KP_")) {
			validated = input.replaceFirst("KP_", "Keypad ");
		} else if (input.startsWith("LEFT_")) {
			validated = input.replaceFirst("LEFT_", "Left ");
		} else if (input.startsWith("RIGHT_")) {
			validated = input.replaceFirst("RIGHT_", "Right ");
		}
		return String.format(KEY_ENUM_CONSTANT_JAVADOC, validated);
	}

	/**
	 * I'm going to go ahead and assume you're in the project directory, okay?
	 * That's /UnplannedDescent/UDKeyboard.
	 * 
	 * @param args
	 *            - The arguments
	 */
	public static void main(String[] args) {
		Array<List<String>> data = collectKeyConstants(GLFW.class);
		List<String> keyNames = ImmutableList.copyOf(Lists.transform(
				data.get(1), CONVERT_TO_JAVA_IDENTIFIERS));
		try (FileWriter fileWriter = new FileWriter(
				"./src/main/java/com/techshroom/unplanned/Key.java");
				JavaWriter writer = new JavaWriter(fileWriter);) {
			writer.setIndent("    ");
			writer.emitPackage("com.techshroom.unplanned");
			writer.emitImports(GLFW.class);
			writer.emitEmptyLine();
			writer.emitJavadoc(KEY_ENUM_JAVADOC);
			Set<javax.lang.model.element.Modifier> PUBLIC = EnumSet
					.of(javax.lang.model.element.Modifier.PUBLIC), PRIVATE = EnumSet
					.of(javax.lang.model.element.Modifier.PRIVATE);
			writer.beginType("Key", "enum", PUBLIC);
			for (int i = 0; i < keyNames.size(); i++) {
				String name = keyNames.get(i);
				System.err.println("Inserting key enum value \"" + name + "\"");
				writer.emitJavadoc(keysToJavadoc(name));
				writer.emitEnumValue(
						name + "(GLFW." + data.get(0).get(1) + ")",
						(i == (keyNames.size() - 1)));
			}
			writer.emitEmptyLine();
			writer.emitField("int", "glfwCode", EnumSet.of(
					javax.lang.model.element.Modifier.PRIVATE,
					javax.lang.model.element.Modifier.FINAL));
			writer.emitEmptyLine();
			writer.beginConstructor(PRIVATE, "int", "glfwCode");
			writer.emitStatement("this.glfwCode = glfwCode");
			writer.endConstructor();
			writer.emitEmptyLine();
			writer.emitJavadoc(GET_GLFWCODE_JAVADOC);
			writer.beginMethod("int", "getGLFWCode", PUBLIC);
			writer.emitStatement("return this.glfwCode");
			writer.endMethod();
			writer.emitEmptyLine();
			writer.emitAnnotation(Override.class);
			writer.beginMethod("String", "toString", PUBLIC);
			writer.emitStatement("return \"GLFW_KEY_\" + name().replaceFirst(\"NUM_(\\\\d)\", \"\\\\1\")");
			writer.endMethod();
			writer.endType();
		} catch (Exception e) {
			Throwables.propagate(e);
		}
	}

	/**
	 * Collects any variable names matching {@code <class name>_KEY_<key name>}
	 * as the key name.
	 * 
	 * <p>
	 * For example, passing {@code GLFW.class} as the class would hit the
	 * {@code GLFW_KEY_A} constant, and the returned array would contain an "A"
	 * in it.
	 * </p>
	 * 
	 * @param target
	 *            - The class to extract key constants from
	 * @return An array of [key constants variable list, key names list]
	 */
	@SuppressWarnings("unchecked")
	private static Array<List<String>> collectKeyConstants(Class<?> target) {
		List<Field> constants = extractConstants(target);
		List<String> key_names = Lists.newArrayList();
		List<String> var_names = Lists.newArrayList();
		Pattern pat = generateKeyNameMatcherForClass(target);
		for (Field field : constants) {
			Matcher m = pat.matcher(field.getName());
			if (m.matches()) {
				key_names.add(m.group(1));
				var_names.add(field.getName());
			}
		}
		return Array.array(var_names, key_names);
	}

	private static Pattern generateKeyNameMatcherForClass(Class<?> target) {
		return Pattern.compile(String.format(KEY_NAME_PATTERN,
				target.getSimpleName()));
	}

	/**
	 * Returns only {@code public static final} fields from the given class.
	 * 
	 * @param target
	 *            - The class to extract constants from
	 * @return All {@code public static final} fields from the given class
	 */
	private static List<Field> extractConstants(Class<?> target) {
		Field[] fields = target.getFields();
		List<Field> constants = Lists.newArrayList(fields);
		for (Field field : fields) {
			int mods = field.getModifiers();
			// constants are public static final fields
			if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods)
					|| !Modifier.isFinal(mods)) {
				// not constant field
				constants.remove(field);
			}
		}
		return constants;
	}
}
