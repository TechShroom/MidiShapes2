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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.OptionalInt;

import javax.annotation.Nullable;
import javax.sound.midi.Sequence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.ChannelAftertouchEvent;
import com.techshroom.midishapes.midi.event.channel.NoteAftertouchEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;
import com.techshroom.midishapes.midi.event.channel.PitchBendEvent;
import com.techshroom.midishapes.midi.event.channel.ProgramChangeEvent;
import com.techshroom.midishapes.midi.event.meta.CopyrightNoticeEvent;
import com.techshroom.midishapes.midi.event.meta.CuePointEvent;
import com.techshroom.midishapes.midi.event.meta.EndOfTrackEvent;
import com.techshroom.midishapes.midi.event.meta.InstrumentNameEvent;
import com.techshroom.midishapes.midi.event.meta.LyricEvent;
import com.techshroom.midishapes.midi.event.meta.MarkerEvent;
import com.techshroom.midishapes.midi.event.meta.SequenceNumberEvent;
import com.techshroom.midishapes.midi.event.meta.SetTempoEvent;
import com.techshroom.midishapes.midi.event.meta.TextEvent;
import com.techshroom.midishapes.midi.event.meta.TrackNameEvent;

public class MidiFileLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiFileLoader.class);

    private static final byte[] HEADER_TAG = "MThd".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRACK_TAG = "MTrk".getBytes(StandardCharsets.US_ASCII);
    private static final int TICKS_PER_FRAME_MASK = 0xFF;

    public static MidiFile load(Path source) throws IOException {
        return new MidiFileLoader(source).load();
    }

    private final Path source;
    private final Deque<InputStream> inputs = new LinkedList<>();

    private final ImmutableList.Builder<MidiTrack> trackList = ImmutableList.builder();
    private MidiTimeEncoding timeEncoding;
    private MidiType midiType;
    private int tracks;

    private MidiFileLoader(Path source) {
        this.source = source;
    }

    private InputStream input() {
        return inputs.peekLast();
    }

    // here we go, loading an ENTIRE file in one method
    private MidiFile load() throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            inputs.addLast(input);
            loadHeaderChunk();
            switch (midiType) {
                case SINGLE_TRACK:
                    checkState(this.tracks == 1, "Format 0 uses only one track!");
                    break;
                case MULTI_TRACK:
                    checkState(this.tracks > 0, "Format 1 requires at least one track");
                    break;
                case REPEATED_SINGLE_TRACK:
                    throw new UnsupportedOperationException("Format 2 is unsupported. Sorry!");
            }

            for (int i = 0; i < tracks; i++) {
                loadTrackChunk();
            }
        } finally {
            if (!inputs.isEmpty()) {
                inputs.removeLast();
            }
        }

        ImmutableList<MidiTrack> tracks = trackList.build();
        checkState(tracks.size() == this.tracks, "loaded wrong number of tracks, somehow?");
        return MidiFile.of(source, midiType, tracks, MidiTiming.calculate(timeEncoding, tracks.get(0)));
    }

    // assumptions made here
    // 1: we're in a file, we can ALWAYS read what we need to
    // 2: the file is a normal file, so the above still applies
    // please no violate, thx

    private void loadHeaderChunk() throws IOException {
        readAndCheckTag("header", HEADER_TAG);
        int length = readInt("length");
        short format;
        short division;
        pushLength(length);
        try {
            format = readShort("format");
            tracks = readShort("tracks");
            division = readShort("divison");
        } finally {
            popLength();
        }

        // set format
        midiType = MidiType.forFormat(format);
        // set division
        if (division > 0) {
            timeEncoding = MidiTimeEncoding.createTPQ(division);
        } else {
            float smpteDivision;
            int divisionInt = -1 * (division >> 8);
            switch (divisionInt) {
                case 24:
                    smpteDivision = Sequence.SMPTE_24;
                    break;
                case 25:
                    smpteDivision = Sequence.SMPTE_25;
                    break;
                case 29:
                    smpteDivision = Sequence.SMPTE_30DROP;
                    break;
                case 30:
                    smpteDivision = Sequence.SMPTE_30;
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown SMPTE division " + divisionInt);
            }
            timeEncoding = MidiTimeEncoding.createSMPTE(smpteDivision, division & TICKS_PER_FRAME_MASK);
        }
    }

    private interface MidiEventConstructor {

        @Nullable
        MidiEvent construct(int tick, OptionalInt activeChannel, int[] data) throws IOException;

    }

    private static final RangeMap<Integer, Entry<Integer, MidiEventConstructor>> eventCreators;

    static {
        ImmutableRangeMap.Builder<Integer, Entry<Integer, MidiEventConstructor>> b = ImmutableRangeMap.builder();

        // channel messages
        b.put(Range.closedOpen(0x80, 0x8F), Maps.immutableEntry(2, (tick, chan, data) -> {
            return NoteOffEvent.create(tick, chan.getAsInt(), data[0], data[1]);
        }));
        b.put(Range.closedOpen(0x90, 0x9F), Maps.immutableEntry(2, (tick, chan, data) -> {
            return NoteOnEvent.create(tick, chan.getAsInt(), data[0], data[1]);
        }));
        b.put(Range.closedOpen(0xA0, 0xAF), Maps.immutableEntry(2, (tick, chan, data) -> {
            return NoteAftertouchEvent.create(tick, chan.getAsInt(), data[0], data[1]);
        }));
        b.put(Range.closedOpen(0xB0, 0xBF), Maps.immutableEntry(2, (tick, chan, data) -> {
            return controlChange(tick, chan, data[0], data[1]);
        }));
        b.put(Range.closedOpen(0xC0, 0xCF), Maps.immutableEntry(1, (tick, chan, data) -> {
            return ProgramChangeEvent.create(tick, chan.getAsInt(), data[0]);
        }));
        b.put(Range.closedOpen(0xD0, 0xDF), Maps.immutableEntry(1, (tick, chan, data) -> {
            return ChannelAftertouchEvent.create(tick, chan.getAsInt(), data[0]);
        }));
        b.put(Range.closedOpen(0xE0, 0xEF), Maps.immutableEntry(2, (tick, chan, data) -> {
            // yes, 7, each value is only 7 bits of data
            return PitchBendEvent.create(tick, chan.getAsInt(), data[0] & (data[1] << 7));
        }));

        eventCreators = b.build();
    }

    @Nullable
    private static MidiEvent controlChange(int tick, OptionalInt chan, int data1, int data2) {
        // I have no clue what * Mode Off is, they all cause AllNotesOffEvent
        // anyways...
        switch (data1) {
            case 123:
            case 124:
            case 125:
            case 126:
            case 127:
                return AllNotesOffEvent.create(tick, chan.getAsInt());
            default:
                return null;
        }
    }

    private void loadTrackChunk() throws IOException {
        ImmutableList.Builder<MidiEvent> events = ImmutableList.builder();
        readAndCheckTag("track", TRACK_TAG);
        int length = readInt("length");
        pushLength(length);
        try {

            int absTick = 0;
            int activeChannel = -1;
            int data1 = -1;
            int status = 0;
            while (true) {
                absTick += readVarInt("delta-time");

                // apparently the next byte can be either:
                // - the first byte of data, using the previous status
                // - or the new status
                int dataOrStatus = readUnsigned("data or status");

                if (dataOrStatus >= 0x80) {
                    // status code
                    status = dataOrStatus;
                    data1 = -1;
                } else {
                    // new data, share status
                    data1 = dataOrStatus;
                }

                switch (status & 0xF0) {
                    case 0x80:
                    case 0x90:
                    case 0xA0:
                    case 0xB0:
                    case 0xC0:
                    case 0xD0:
                    case 0xE0:
                        // channel from status
                        activeChannel = status & 0xF;
                        break;
                    case 0xF0:
                        // channel from midi event
                        if (status == 0xFF) {
                            data1 = readUnsignedIfNotPresent("data1", data1);
                            // 0x20 == channel prefix event
                            if (data1 == 0x20) {
                                int metaLen = readVarInt("length");
                                pushLength(metaLen);
                                try {
                                    activeChannel = readUnsigned("channel");
                                    // in this case we continue directly to next
                                    // event
                                    // we don't store the channel prefix event
                                    continue;
                                } finally {
                                    popLength();
                                }
                            }
                        }
                }

                OptionalInt channel = activeChannel == -1 ? OptionalInt.empty() : OptionalInt.of(activeChannel);
                MidiEvent event;
                // special cases
                if (status == 0xFF) {
                    data1 = readUnsignedIfNotPresent("meta ID", data1);
                    int len = readVarInt("length");
                    pushLength(len);
                    try {
                        event = metaEvent(absTick, channel, data1);
                    } finally {
                        popLength();
                    }
                } else {
                    Entry<Integer, MidiEventConstructor> cons = eventCreators.get(status);
                    checkState(cons != null, "unexpected status code %s", Integer.toHexString(status));
                    int dataBytes = cons.getKey();
                    checkState(dataBytes == 1 || dataBytes == 2, "incorrect data byte amount %s", dataBytes);
                    int[] data = dataBytes == 1 ? data1Array : data2Array;
                    data[0] = readUnsignedIfNotPresent("data1", data1);
                    if (dataBytes == 2) {
                        data[1] = readUnsigned("data2");
                    }
                    event = cons.getValue().construct(absTick, channel, data);
                }
                if (event instanceof EndOfTrackEvent) {
                    // don't record EOT in the track, it's quite obvious...
                    break;
                }
                if (event != null) {
                    events.add(event);
                }
            }
            trackList.add(MidiTrack.wrap(events.build(), absTick));
        } finally {
            popLength();
        }
    }

    @Nullable
    private MidiEvent metaEvent(int tick, OptionalInt channel, int id) throws IOException {
        switch (id) {
            case 0x00:
                return SequenceNumberEvent.create(tick, channel, readShort("sequence number"));
            case 0x01:
                return TextEvent.create(tick, channel, readText("text"));
            case 0x02:
                return CopyrightNoticeEvent.create(tick, channel, readText("copyright notice"));
            case 0x03:
                return TrackNameEvent.create(tick, channel, readText("track name"));
            case 0x04:
                return InstrumentNameEvent.create(tick, channel, readText("instrument name"));
            case 0x05:
                return LyricEvent.create(tick, channel, readText("lyric"));
            case 0x06:
                return MarkerEvent.create(tick, channel, readText("marker"));
            case 0x07:
                return CuePointEvent.create(tick, channel, readText("cue"));
            case 0x2F:
                return EndOfTrackEvent.create(tick, channel);
            case 0x51:
                return SetTempoEvent.create(tick, channel, read24BitValue("tempo"));

            // Hey you!
            // Open an issue or make a PR if you want one of these supported!
            case 0x54:
                LOGGER.info("Ignoring SMPTE offset event, deemed as unlikely to be encountered by author");
                return null;
            case 0x58:
                LOGGER.info("Ignoring time signature event, deemed as not useful by author");
                return null;
            case 0x59:
                LOGGER.info("Ignoring key signature event, deemed as not useful by author");
                return null;
            default:
                return null;
        }
    }

    // helper reader methods

    private void pushLength(int length) {
        inputs.addLast(ByteStreams.limit(input(), length));
    }

    private void popLength() throws IOException {
        ByteStreams.exhaust(input());
        inputs.removeLast();
    }

    // with helper fields here
    private final int[] data1Array = new int[1];
    private final int[] data2Array = new int[2];
    private final byte[] readSingleArray = new byte[1];
    private final byte[] readTagArray = new byte[4];
    private final byte[] readShortArray = new byte[Short.BYTES];
    private final byte[] read24Array = new byte[3];
    private final byte[] readIntArray = new byte[Integer.BYTES];

    private void readFully(String description, byte[] array) throws IOException {
        if (ByteStreams.read(input(), array, 0, array.length) != array.length) {
            throw new EOFException("Reached EOF before " + description);
        }
    }

    private int readUnsignedIfNotPresent(String description, int current) throws IOException {
        if (current == -1) {
            return readUnsigned(description);
        }
        return current;
    }

    private int readUnsigned(String description) throws IOException {
        return readByte(description) & 0xFF;
    }

    private byte readByte(String description) throws IOException {
        readFully(description, readSingleArray);
        return readSingleArray[0];
    }

    private short readShort(String description) throws IOException {
        readFully(description, readShortArray);
        return (short) (((readShortArray[0] & 0xFF) << 8) + ((readShortArray[1] & 0xFF)));
    }

    private int read24BitValue(String description) throws IOException {
        readFully(description, read24Array);
        return ((read24Array[0] & 0xFF) << 16)
                + ((read24Array[1] & 0xFF) << 8)
                + ((read24Array[2] & 0xFF));
    }

    private int readInt(String description) throws IOException {
        readFully(description, readIntArray);
        return ((readIntArray[0] & 0xFF) << 24)
                + ((readIntArray[1] & 0xFF) << 16)
                + ((readIntArray[2] & 0xFF) << 8)
                + ((readIntArray[3] & 0xFF));
    }

    private int readVarInt(String description) throws IOException {
        int value = 0;
        int currentByte = 0;
        do {
            currentByte = readUnsigned(description);
            value = (value << 7) + (currentByte & 0x7F);
        } while ((currentByte & 0x80) != 0);
        return value;
    }

    private void readAndCheckTag(String tagType, byte[] expectedTag) throws IOException {
        readFully("MIDI chunk tag", readTagArray);
        checkState(Arrays.equals(readTagArray, expectedTag),
                "Not a %s tag: %s", tagType, Arrays.toString(readTagArray));
    }

    private String readText(String description) throws IOException {
        // text is just until end of current stream
        return CharStreams.toString(new InputStreamReader(input(), StandardCharsets.US_ASCII));
    }

}
