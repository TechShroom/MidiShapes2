/*
 * This file is part of UnplannedDescent, licensed under the MIT License (MIT).
 *
 * Copyright (c) TechShroom Studios <https://techshroom.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.techshroom.midishapes.util;

import static org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.lwjgl.PointerBuffer;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

public class CrossThreadFilePicker {

    private final String prompt;
    private final PointerBuffer fileFilter;
    private final String fileFilterName;
    private Path currentDirectory = Paths.get(".");
    private ReadOnlyObjectWrapper<Path> currentValueProperty = new ReadOnlyObjectWrapper<>(this, "currentValueProperty");
    private transient volatile Path threadTransfer;

    public CrossThreadFilePicker(String prompt, PointerBuffer fileFilter, String fileFilterName, Path currentDirectory) {
        this.prompt = prompt;
        this.fileFilter = fileFilter;
        this.fileFilterName = fileFilterName;
        this.currentDirectory = currentDirectory;
    }

    public ReadOnlyObjectProperty<Path> currentValueProperty() {
        return currentValueProperty.getReadOnlyProperty();
    }

    public Path getCurrentValue() {
        return currentValueProperty.get();
    }

    public void transferIfNeeded() {
        if (threadTransfer != null) {
            currentValueProperty.set(threadTransfer);
            threadTransfer = null;
        }
    }

    public void showDialog() {
        String file = tinyfd_openFileDialog(prompt, currentDirectory.toAbsolutePath().toString() + "/", fileFilter, fileFilterName, false);
        if (file != null) {
            Path path = Paths.get(file);
            threadTransfer = path;
            currentDirectory = path.getParent();
        }
    }

}
