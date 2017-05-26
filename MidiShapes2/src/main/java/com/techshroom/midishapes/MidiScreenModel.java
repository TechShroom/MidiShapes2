/*
 * This file is part of UnplannedDescent, licensed under the MIT License (MIT).
 *
 * Copyright (c) TechShroom Studios <https://techshoom.com>
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
package com.techshroom.midishapes;

import static org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import com.google.common.eventbus.Subscribe;
import com.techshroom.midishapes.midi.MidiFile;
import com.techshroom.midishapes.midi.MidiFileLoader;
import com.techshroom.unplanned.core.util.LifecycleObject;
import com.techshroom.unplanned.event.keyboard.KeyState;
import com.techshroom.unplanned.event.keyboard.KeyStateEvent;
import com.techshroom.unplanned.input.Key;
import com.techshroom.unplanned.window.Window;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;

public class MidiScreenModel implements LifecycleObject {

    // trailing slash is IMPORTANT -- it opens it to the folder rather than the
    // parent
    private static final String defaultOpenFolder = Paths.get(System.getProperty("user.home")).toAbsolutePath().toString() + "/";
    private static final PointerBuffer midiFileFilter = BufferUtils.createPointerBuffer(2);
    static {
        midiFileFilter.put(0, MemoryUtil.memUTF8("*.mid"));
        midiFileFilter.put(1, MemoryUtil.memUTF8("*.midi"));
    }

    private final Window window;

    @Inject
    MidiScreenModel(Window window) {
        this.window = window;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void destroy() {
    }

    // properties of the model

    private final ObjectProperty<Path> openFileProperty = new SimpleObjectProperty<>(this, "openFile");
    private final ReadOnlyObjectWrapper<MidiFile> openMidiFileBinding = new ReadOnlyObjectWrapper<>(this, "openMidiFile");

    {
        openFileProperty.addListener(new InvalidationListener() {

            @Override
            public void invalidated(Observable arg0) {
                if (openFileProperty.get() == null) {
                    return;
                }
                try {
                    openMidiFileBinding.set(MidiFileLoader.load(openFileProperty.get()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    public ReadOnlyObjectProperty<MidiFile> openMidiFileBinding() {
        return openMidiFileBinding.getReadOnlyProperty();
    }

    public ObjectProperty<Path> openFileProperty() {
        return openFileProperty;
    }

    public Path getOpenFile() {
        return openFileProperty.get();
    }

    public void setOpenFile(Path file) {
        openFileProperty.set(file);
    }

    @Subscribe
    public void onKey(KeyStateEvent event) {
        if (event.is(Key.O, KeyState.PRESSED)) {
            String file = tinyfd_openFileDialog("Pick a MIDI File", defaultOpenFolder, midiFileFilter, "MIDI Files", false);
            if (file != null) {
                setOpenFile(Paths.get(file));
            }
        } else if (event.is(Key.SPACE, KeyState.PRESSED)) {
            
        } else if (event.is(Key.ESCAPE, KeyState.PRESSED)) {
            window.setCloseRequested(true);
        }
    }

}
