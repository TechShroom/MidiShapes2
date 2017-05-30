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
package com.techshroom.midishapes.midi.player;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
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
import com.techshroom.midishapes.midi.event.meta.InstrumentNameEvent;
import com.techshroom.midishapes.midi.event.meta.SetTempoEvent;
import com.techshroom.midishapes.midi.event.meta.TextEvent;
import com.techshroom.midishapes.midi.event.meta.TrackNameEvent;

class MidiEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiEventHandler.class);

    private static final ImmutableSet<Class<?>> KNOWN_UNUSED = ImmutableSet.of(
            // tempo is pre-calculated
            SetTempoEvent.class,
            // controller events are always unused
            ControllerEvent.class,
            // text events we don't consume
            TextEvent.class, TrackNameEvent.class, InstrumentNameEvent.class);

    private static boolean isKnownUnused(Class<?> c) {
        Deque<Class<?>> classes = new LinkedList<>();
        classes.addLast(c);
        while (!classes.isEmpty()) {
            Class<?> pop = classes.pop();

            if (KNOWN_UNUSED.contains(pop)) {
                return true;
            }

            Class<?> superclass = pop.getSuperclass();
            if (superclass != null) {
                classes.add(superclass);
            }
            Collections.addAll(classes, pop.getInterfaces());
        }

        return false;
    }

    private final EventBus events;
    private final MidiSoundPlayer sounds;

    MidiEventHandler(MidiSoundPlayer sounds, EventBus events) {
        this.events = checkNotNull(events, "events");
        this.sounds = checkNotNull(sounds, "sounds");
        events.register(this);
    }

    void onEvent(MidiEvent event) {
        events.post(event);
    }

    @Subscribe
    public void deadEvent(DeadEvent event) {
        Object e = event.getEvent();
        if (!isKnownUnused(e.getClass())) {
            LOGGER.warn("Unhandled event {}", e);
        }
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
