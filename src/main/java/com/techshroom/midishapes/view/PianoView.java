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

package com.techshroom.midishapes.view;

import java.util.concurrent.atomic.AtomicIntegerArray;

import com.techshroom.midishapes.midi.event.StopEvent;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;
import com.techshroom.midishapes.midi.player.MidiEventChainLink;
import com.techshroom.midishapes.midi.player.MidiEventHandlerMap;

/**
 * Holds state for rendering each piano.
 */
final class PianoView implements MidiEventChainLink.Hub {

    static final int PIANO_SIZE = 128;

    private final AtomicIntegerArray keys = new AtomicIntegerArray(PIANO_SIZE);

    private final MidiEventHandlerMap handlerMap = new MidiEventHandlerMap();
    {
        handlerMap.put(NoteOnEvent.class, this::noteOn);
        handlerMap.put(NoteOffEvent.class, this::noteOff);
        handlerMap.put(AllNotesOffEvent.class, this::allOff);
        handlerMap.put(StopEvent.class, this::stop);
    }
    private final int channel;

    PianoView(int channel) {
        this.channel = channel;
    }

    AtomicIntegerArray getKeyArray() {
        return keys;
    }

    public boolean isDown(int key) {
        return getVelocity(key) != 0;
    }

    public int getVelocity(int key) {
        return keys.get(key);
    }

    @Override
    public MidiEventHandlerMap getHandlerMap() {
        return handlerMap;
    }

    public void noteOn(NoteOnEvent event) {
        if (event.getChannel() != channel) {
            return;
        }
        keys.set(event.getNote(), event.getVelocity());
    }

    public void noteOff(NoteOffEvent event) {
        if (event.getChannel() != channel) {
            return;
        }
        keys.set(event.getNote(), 0);
    }

    public void allOff(AllNotesOffEvent event) {
        for (int i = 0; i < PIANO_SIZE; i++) {
            keys.set(i, 0);
        }
    }

    public void stop(StopEvent event) {
        reset();
    }

    public void reset() {
        for (int i = 0; i < PIANO_SIZE; i++) {
            keys.set(i, 0);
        }
    }

}
