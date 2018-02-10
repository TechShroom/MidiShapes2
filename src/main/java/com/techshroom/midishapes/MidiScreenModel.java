/*
 * This file is part of MidiShapes2, licensed under the MIT License (MIT).
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
package com.techshroom.midishapes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import com.google.common.eventbus.Subscribe;
import com.techshroom.midishapes.midi.MidiFile;
import com.techshroom.midishapes.midi.MidiFileLoader;
import com.techshroom.midishapes.midi.player.MidiEventChain;
import com.techshroom.midishapes.midi.player.MidiPlayer;
import com.techshroom.midishapes.midi.player.MidiSoundPlayer;
import com.techshroom.midishapes.util.CrossThreadFilePicker;
import com.techshroom.unplanned.core.util.LifecycleObject;
import com.techshroom.unplanned.event.keyboard.KeyState;
import com.techshroom.unplanned.event.keyboard.KeyStateEvent;
import com.techshroom.unplanned.input.Key;
import com.techshroom.unplanned.window.Window;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;

public class MidiScreenModel implements LifecycleObject {

    private static final PointerBuffer midiFileFilter = BufferUtils.createPointerBuffer(2);
    private static final PointerBuffer soundfontFileFilter = BufferUtils.createPointerBuffer(1);
    static {
        midiFileFilter.put(0, MemoryUtil.memUTF8("*.mid"));
        midiFileFilter.put(1, MemoryUtil.memUTF8("*.midi"));

        soundfontFileFilter.put(0, MemoryUtil.memUTF8("*.sf2"));
    }

    private final ExecutorService pool;
    private final Window window;
    private final MidiPlayer player;
    private final ObjectProperty<MidiSoundPlayer> soundPlayer;
    private final ObjectBinding<MidiEventChain> chain;
    private final CrossThreadFilePicker midiFilePicker = new CrossThreadFilePicker("Pick a MIDI file", midiFileFilter, "MIDI Files",
            Paths.get(System.getProperty("user.home")).resolve("Documents/misc_midi"));
    private final CrossThreadFilePicker sfFilePicker = new CrossThreadFilePicker("Pick a soundfont", soundfontFileFilter, "SF2 Files",
            Paths.get(System.getProperty("user.home")).resolve("Documents"));

    @Inject
    MidiScreenModel(ExecutorService pool, Window window, MidiPlayer player,
            ObjectProperty<MidiSoundPlayer> soundPlayer, ObjectBinding<MidiEventChain> chain) {
        this.pool = pool;
        this.window = window;
        this.player = player;
        this.soundPlayer = soundPlayer;
        this.chain = chain;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void destroy() {
    }

    public void mainLoop() {
        midiFilePicker.transferIfNeeded();
        sfFilePicker.transferIfNeeded();
    }

    // properties of the model

    private final ReadOnlyObjectWrapper<MidiFile> openMidiFileProperty = new ReadOnlyObjectWrapper<>(this, "openMidiFile");
    private final BooleanProperty loopingProperty = new SimpleBooleanProperty(this, "looping");

    {
        midiFilePicker.currentValueProperty().addListener(new InvalidationListener() {

            @Override
            public void invalidated(Observable arg0) {
                player.stop();
                Path path = midiFilePicker.getCurrentValue();
                if (path == null) {
                    return;
                }
                try {
                    openMidiFileProperty.set(MidiFileLoader.load(path));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        sfFilePicker.currentValueProperty().addListener(new InvalidationListener() {

            @Override
            public void invalidated(Observable arg0) {
                Path value = sfFilePicker.getCurrentValue();
                if (value != null) {
                    soundPlayer.get().setSoundfont(value);
                }
            }
        });
    }

    public ReadOnlyObjectProperty<MidiFile> openMidiFileProperty() {
        return openMidiFileProperty.getReadOnlyProperty();
    }

    public BooleanProperty loopingProperty() {
        return loopingProperty;
    }

    public boolean isLooping() {
        return loopingProperty.get();
    }

    public void setLooping(boolean looping) {
        loopingProperty.set(looping);
    }

    @Subscribe
    public void onKey(KeyStateEvent event) {
        if (event.is(Key.O, KeyState.RELEASED)) {
            pool.submit(midiFilePicker::showDialog);
        } else if (event.is(Key.L, KeyState.RELEASED)) {
            setLooping(!isLooping());
        } else if (event.is(Key.SPACE, KeyState.RELEASED)) {
            if (this.player.isRunning()) {
                this.player.stop();
            } else {
                MidiFile file = openMidiFileProperty.get();
                if (file != null) {
                    this.player.play(file, chain.get());
                }
            }
        } else if (event.is(Key.J, KeyState.RELEASED)) {
            pool.submit(sfFilePicker::showDialog);
        } else if (event.is(Key.H, KeyState.RELEASED)) {
            this.soundPlayer.get().openSettingsPanel();
        } else if (event.is(Key.ESCAPE, KeyState.RELEASED)) {
            window.setCloseRequested(true);
        }
    }

}
