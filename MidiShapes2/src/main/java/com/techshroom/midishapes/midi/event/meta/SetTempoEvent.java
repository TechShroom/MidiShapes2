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
package com.techshroom.midishapes.midi.event.meta;

import java.util.concurrent.TimeUnit;

import com.google.auto.value.AutoValue;
import com.techshroom.midishapes.midi.event.MidiEvent;

// tempo in microseconds per quarter note
@AutoValue
public abstract class SetTempoEvent implements MidiEvent {

    public static final long MICROSECONDS_PER_MINUTE = TimeUnit.MINUTES.toMicros(1);

    public static SetTempoEvent createBpm(int tick, int channel, double bpm) {
        // have bpm == beats per minute
        // have MPM == micros per minute
        // want: micros per beat
        // MPM / bpm == (u/m)*(m/b) == (u/b)
        return create(tick, channel, (int) (MICROSECONDS_PER_MINUTE / bpm));
    }

    public static SetTempoEvent create(int tick, int channel, int tempo) {
        return new AutoValue_SetTempoEvent(tick, channel, tempo);
    }

    SetTempoEvent() {
    }

    public abstract int getTempo();

    public final double getBeatsPerMinute() {
        // have tempo == micros per beat
        // have MPM == micros per minute
        // want: beats per minute
        // MPM / tempo == (u/m)*(b/u) == (b/m)
        return MICROSECONDS_PER_MINUTE / (double) getTempo();
    }

}
