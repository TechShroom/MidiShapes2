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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.techshroom.midishapes.midi.MidiFile;
import com.techshroom.midishapes.midi.MidiFileLoader;
import com.techshroom.midishapes.midi.player.MidiPlayer;
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
    private static String defaultOpenFolder = Paths.get(System.getProperty("user.home")).toAbsolutePath().toString() + "/Dropbox/";
    private static final PointerBuffer midiFileFilter = BufferUtils.createPointerBuffer(2);
    static {
        midiFileFilter.put(0, MemoryUtil.memUTF8("*.mid"));
        midiFileFilter.put(1, MemoryUtil.memUTF8("*.midi"));
    }
    private static final ExecutorService POOL = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("model-workers-%d").build());

    private final Window window;
    private final MidiPlayer player;
    private volatile Path openFileTransfer;

    @Inject
    MidiScreenModel(Window window, MidiPlayer player) {
        this.window = window;
        this.player = player;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void destroy() {
    }

    public void mainLoop() {
        if (openFileTransfer != null) {
            setOpenFile(openFileTransfer);
            openFileTransfer = null;
        }
    }

    // properties of the model

    private final ObjectProperty<Path> openFileProperty = new SimpleObjectProperty<>(this, "openFile");
    private final ReadOnlyObjectWrapper<MidiFile> openMidiFileBinding = new ReadOnlyObjectWrapper<>(this, "openMidiFile");

    {
        openFileProperty.addListener(new InvalidationListener() {

            @Override
            public void invalidated(Observable arg0) {
                player.stop();
                Path path = openFileProperty.get();
                if (path == null) {
                    return;
                }
                defaultOpenFolder = path.toString();
                try {
                    openMidiFileBinding.set(MidiFileLoader.load(path));
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
            POOL.submit(() -> {
                String file = tinyfd_openFileDialog("Pick a MIDI File", defaultOpenFolder, midiFileFilter, "MIDI Files", false);
                if (file != null) {
                    openFileTransfer = Paths.get(file);
                }
            });
        } else if (event.is(Key.SPACE, KeyState.PRESSED)) {
            if (this.player.isRunning()) {
                this.player.stop();
            } else {
                MidiFile file = openMidiFileBinding.get();
                if (file != null) {
                    this.player.play(file);
                }
            }
        } else if (event.is(Key.ESCAPE, KeyState.PRESSED)) {
            window.setCloseRequested(true);
        }
    }

}
