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
package com.techshroom.midishapes.midi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.techshroom.midishapes.midi.event.meta.SetTempoEvent;

/**
 * MIDI timing is a fun bag of beans!
 */
public class MidiTiming {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiTiming.class);

    /**
     * Calculate MIDI timings. Does not work properly for
     * {@link MidiType#REPEATED_SINGLE_TRACK}, which has timing data in each
     * track that could be different.
     * 
     * @param tempoTrack
     *            the track that contains the tempo values
     * @return
     */
    public static MidiTiming calculate(MidiTimeEncoding timeEncoding, List<MidiTrack> tracks) {
        List<SetTempoEvent> tempoEvents = tracks.stream().flatMap(t -> t.getEvents().stream())
                .filter(SetTempoEvent.class::isInstance)
                .map(SetTempoEvent.class::cast)
                .sorted(Comparator.comparingInt(SetTempoEvent::getTick))
                .collect(Collectors.toCollection(ArrayList::new));

        SetTempoEvent prev;
        // check if we need a fake 120 BPM to start
        if (tempoEvents.isEmpty() || tempoEvents.get(0).getTick() != 0) {
            // no tempo events OR no zero tick event
            prev = SetTempoEvent.createBpm(0, 0, 120);
            LOGGER.info("No initial tempo set in track, using 120 BPM");
        } else {
            // zero tick event
            prev = tempoEvents.get(0);
            tempoEvents.remove(0);
        }

        ImmutableRangeMap.Builder<Integer, TickOffsetTempoCalculator> tempoCalculators = ImmutableRangeMap.builder();
        int[] currentMicrosOffset = { 0 };
        ObjIntConsumer<SetTempoEvent> addTC = (event, latestTick) -> {
            if (event.getTick() == latestTick) {
                return;
            }
            tempoCalculators.put(Range.openClosed(event.getTick(), latestTick),
                    new TickOffsetTempoCalculator(currentMicrosOffset[0], timeEncoding, event));
        };
        for (SetTempoEvent event : tempoEvents) {
            // add previous entry
            addTC.accept(prev, event.getTick());
            // compute micros since prev STE
            int tickDiff = event.getTick() - prev.getTick();
            currentMicrosOffset[0] += tickDiff * (timeEncoding.getMicrosecondsPerTick(prev.getTempo()));
            prev = event;
        }

        // add final entry
        addTC.accept(prev, Integer.MAX_VALUE);

        return new MidiTiming(tempoCalculators.build());
    }

    private static final class TickOffsetTempoCalculator {

        private final int baseMicros;
        private final int microsPerTick;
        private final SetTempoEvent tempoEvent;

        public TickOffsetTempoCalculator(int baseMicros, MidiTimeEncoding timeEncoding, SetTempoEvent tempoEvent) {
            this.baseMicros = baseMicros;
            this.microsPerTick = timeEncoding.getMicrosecondsPerTick(tempoEvent.getTempo());
            this.tempoEvent = tempoEvent;
        }

        public int getMicrosecondOffset(int tick) {
            int tickDiff = tick - tempoEvent.getTick();
            int addMicros = tickDiff * microsPerTick;
            return baseMicros + addMicros;
        }

    }

    private final RangeMap<Integer, TickOffsetTempoCalculator> tickCalc;

    private MidiTiming(RangeMap<Integer, TickOffsetTempoCalculator> tempoCalculators) {
        this.tickCalc = tempoCalculators;
    }

    /**
     * Map a MIDI-tick to the millis offset from the start of the track.
     * 
     * @param tick
     *            - the tick from the track
     * @return the millisecond offset from the start of the track
     */
    public long getMillisecondOffset(int tick) {
        if (tick == 0) {
            return 0;
        }
        return TimeUnit.MICROSECONDS.toMillis(tickCalc.get(tick).getMicrosecondOffset(tick));
    }

}
