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
package com.techshroom.midishapes.midi.player;

import static com.google.common.base.Preconditions.checkState;
import static org.lwjgl.openal.ALC10.alcOpenDevice;

import java.nio.ByteBuffer;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;

import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;
import com.techshroom.midishapes.midi.event.channel.PitchBendEvent;
import com.techshroom.midishapes.midi.event.channel.ProgramChangeEvent;

/**
 * Plays sounds using the provided sound font.
 */
class MidiSoundManager implements MidiSoundPlayer {

    static {
        long dev = alcOpenDevice((ByteBuffer) null);
        checkState(dev != 0, "no sound device found");
        AL.createCapabilities(ALC.createCapabilities(dev));
    }

    static MidiSoundManager manangeFont(MidiSoundfont sounds) {
        return new MidiSoundManager();
    }

    private MidiSoundManager() {
    }

    @Override
    public void changeProgram(ProgramChangeEvent event) {

    }

    @Override
    public void noteOn(NoteOnEvent event) {

    }

    @Override
    public void noteOff(NoteOffEvent event) {

    }

    @Override
    public MidiSoundPlayer open() {
        return this;
    }

    @Override
    public void close() {

    }

    @Override
    public void allNotesOff(AllNotesOffEvent event) {
    }

    @Override
    public void pitchBend(PitchBendEvent event) {
    }

}
