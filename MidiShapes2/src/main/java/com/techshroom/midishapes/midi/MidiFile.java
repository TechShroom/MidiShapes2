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
package com.techshroom.midishapes.midi;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

@AutoValue
public abstract class MidiFile {

    public static MidiFile of(Path path, MidiType type, Collection<Integer> channels, List<MidiTrack> tracks, MidiTiming timing) {
        switch (type) {
            case SINGLE_TRACK:
                checkState(tracks.size() == 1, "SINGLE_TRACK requires 1 track");
                break;
            case MULTI_TRACK:
            case REPEATED_SINGLE_TRACK:
                checkState(tracks.size() > 0, "%s requires at least 1 track", type);
                break;
        }
        ImmutableSet<Integer> sortedChannels = channels.stream().sorted().collect(toImmutableSet());
        return new AutoValue_MidiFile(path, type, sortedChannels, ImmutableList.copyOf(tracks), timing);
    }

    MidiFile() {
    }

    public abstract Path getPath();

    public abstract MidiType getType();

    public abstract ImmutableSet<Integer> getChannels();

    public abstract ImmutableList<MidiTrack> getTracks();

    public abstract MidiTiming getTimingData();

}
