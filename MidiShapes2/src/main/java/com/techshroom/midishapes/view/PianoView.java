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
package com.techshroom.midishapes.view;

import java.util.concurrent.atomic.AtomicIntegerArray;

import com.google.common.eventbus.Subscribe;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;

/**
 * Holds state for rendering each piano.
 */
class PianoView {

    private static final int PIANO_SIZE = 128;

    private final AtomicIntegerArray keys = new AtomicIntegerArray(PIANO_SIZE);

    private final int channel;

    PianoView(int channel) {
        this.channel = channel;
    }

    public boolean isDown(int key) {
        return getVelocity(key) != 0;
    }

    public int getVelocity(int key) {
        return keys.get(key);
    }

    @Subscribe
    public void noteOn(NoteOnEvent event) {
        if (event.getChannel() != channel) {
            return;
        }
        keys.set(event.getNote(), event.getVelocity());
    }

    @Subscribe
    public void noteOff(NoteOffEvent event) {
        if (event.getChannel() != channel) {
            return;
        }
        keys.set(event.getNote(), 0);
    }

    @Subscribe
    public void allOff(AllNotesOffEvent event) {
        for (int i = 0; i < PIANO_SIZE; i++) {
            keys.set(i, 0);
        }
    }

    public void reset() {
        for (int i = 0; i < PIANO_SIZE; i++) {
            keys.set(i, 0);
        }
    }

}
