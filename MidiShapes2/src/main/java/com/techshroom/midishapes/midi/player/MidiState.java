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

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.ControllerEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;
import com.techshroom.midishapes.midi.event.channel.PitchBendEvent;
import com.techshroom.midishapes.midi.event.channel.ProgramChangeEvent;
import com.techshroom.midishapes.midi.event.meta.SetTempoEvent;

class MidiState {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiState.class);

    private final EventBus events;
    private final MidiSoundPlayer sounds;

    MidiState(MidiSoundPlayer sounds, EventBus events) {
        this.events = checkNotNull(events, "events");
        this.sounds = checkNotNull(sounds, "sounds");
        events.register(this);
    }

    void onEvent(MidiEvent event) {
        events.post(event);
    }

    @Subscribe
    public void deadEvent(DeadEvent event) {
        LOGGER.warn("Unhandled event " + event.getEvent());
    }

    @Subscribe
    public void setTempoUnused(SetTempoEvent event) {
        // we precalculated timing info already, event is ignored
    }

    @Subscribe
    public void controllerEventUnused(ControllerEvent event) {
        // all ControllerEvents are unused by default
    }

    // forwards to sound player

    @Subscribe
    public void changeProgram(ProgramChangeEvent event) {
        sounds.changeProgram(event);
    }

    @Subscribe
    public void noteOn(NoteOnEvent event) {
        sounds.noteOn(event);
    }

    @Subscribe
    public void noteOff(NoteOffEvent event) {
        sounds.noteOff(event);
    }

    @Subscribe
    public void allNotesOff(AllNotesOffEvent event) {
        sounds.allNotesOff(event);
    }

    @Subscribe
    public void pitchBend(PitchBendEvent event) {
        sounds.pitchBend(event);
    }

}
