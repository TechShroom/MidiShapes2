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
package com.techshroom.midishapes.midi;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;
import javax.sound.midi.Sequence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.BankSelectEvent;
import com.techshroom.midishapes.midi.event.channel.ChannelAftertouchEvent;
import com.techshroom.midishapes.midi.event.channel.ControllerEvent;
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
import com.techshroom.midishapes.util.ExposedByteArrayInputStream;

public class MidiFileLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiFileLoader.class);

    private static final byte[] HEADER_TAG = "MThd".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRACK_TAG = "MTrk".getBytes(StandardCharsets.US_ASCII);
    private static final int TICKS_PER_FRAME_MASK = 0xFF;

    public static MidiFile load(Path source) throws IOException {
        return new MidiFileLoader(source).load();
    }

    private static long index(int track, int localIndex) {
        return (((long) track) << 32) | localIndex;
    }

    private final ListeningExecutorService loaderThreads = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("midifileloader-%d").build()));
    private final Path source;
    private final ThreadLocal<Deque<InputStream>> inputs = ThreadLocal.withInitial(LinkedList::new);

    private MidiTimeEncoding timeEncoding;
    private MidiType midiType;
    private int tracks;

    private MidiFileLoader(Path source) {
        this.source = source;
    }

    private Deque<InputStream> inputs() {
        return inputs.get();
    }

    private InputStream input() {
        return inputs().peekLast();
    }

    // here we go, loading an ENTIRE file in one method
    private MidiFile load() throws IOException {
        ImmutableList<MidiTrack> tracks;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
            inputs().addLast(input);
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

            ListenableFuture<List<MidiTrack>> trackList = Futures.allAsList(IntStream.range(0, this.tracks)
                    .mapToObj(t -> {
                        try {
                            readAndCheckTag("track", TRACK_TAG);
                            int length = readInt("length");
                            byte[] data;
                            pushLength(length);
                            try {
                                data = ByteStreams.toByteArray(input());
                            } finally {
                                popLength();
                            }
                            return loaderThreads.submit(loadTrackChunk(t, data));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).collect(toImmutableList()));
            tracks = ImmutableList.copyOf(trackList.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            Throwables.throwIfUnchecked(t);
            throw new RuntimeException(t);
        } finally {
            inputs().clear();
        }

        checkState(tracks.size() == this.tracks, "loaded wrong number of tracks, somehow?");
        // scan for channels in parallel
        ImmutableSet<Integer> channels = tracks.stream()
                .flatMap(mt -> mt.getEvents().stream())
                // only note channels that are going to be played on
                .filter(NoteOnEvent.class::isInstance)
                .mapToInt(MidiEvent::getChannel)
                .distinct()
                .boxed()
                .parallel()
                .collect(toImmutableSet());
        return MidiFile.of(source, midiType, channels, tracks, MidiTiming.calculate(timeEncoding, tracks));
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

    private Callable<MidiTrack> loadTrackChunk(int track, byte[] trackChunk) {
        return () -> {
            inputs().push(new ByteArrayInputStream(trackChunk));
            try {
                ImmutableList.Builder<MidiEvent> events = ImmutableList.builder();

                int localIndex = 0;
                int absTick = 0;
                int activeChannel = track;
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
                                        // in this case we continue directly to
                                        // next
                                        // event
                                        // we don't store the channel prefix
                                        // event
                                        continue;
                                    } finally {
                                        popLength();
                                    }
                                }
                            }
                    }

                    MidiEvent event;
                    // special cases
                    if (status == 0xFF) {
                        data1 = readUnsignedIfNotPresent("meta ID", data1);
                        int len = readVarInt("length");
                        pushLength(len);
                        try {
                            event = metaEvent(index(track, localIndex), absTick, activeChannel, data1);
                        } finally {
                            popLength();
                        }
                    } else if (0xF0 <= status && status < 0xFF) {
                        switch (status) {
                            case 0xF0:
                            case 0xF7:
                                // sysex, read & ignore
                                int len = readVarInt("length");
                                // basically drains section w/ length (due to
                                // popLength)
                                pushLength(len);
                                popLength();
                                LOGGER.info("Ignoring sysex...");
                                event = null;
                                break;
                            case 0xF2:
                                // Song Position Pointer -- ignore for now
                                readByte("spp-lsb");
                                readByte("spp-msb");
                                event = null;
                                break;
                            case 0xF3:
                                // song select -- ignore
                                readByte("song");
                                event = null;
                                break;
                            default:
                                // misc system related stuff, ignore it
                                event = null;
                                break;
                        }
                    } else if (0xB0 <= status && status <= 0xBF) {
                        data1 = readUnsignedIfNotPresent("data1", data1);
                        int data2 = readUnsigned("data2");
                        event = controlChange(index(track, localIndex), absTick, activeChannel, data1, data2);
                    } else {
                        Entry<Integer, MidiEventConstructor> cons = eventCreators.get(status);
                        checkState(cons != null, "unexpected status code %s", Integer.toHexString(status));
                        int dataBytes = cons.getKey();
                        checkState(dataBytes == 1 || dataBytes == 2, "incorrect data byte amount %s", dataBytes);
                        int[] data = dataBytes == 1 ? data1Array.get() : data2Array.get();
                        data[0] = readUnsignedIfNotPresent("data1", data1);
                        if (dataBytes == 2) {
                            data[1] = readUnsigned("data2");
                        }
                        event = cons.getValue().construct(index(track, localIndex), absTick, activeChannel, data);
                    }
                    if (event instanceof EndOfTrackEvent) {
                        // don't record EOT in the track, it's quite obvious...
                        break;
                    }
                    if (event != null) {
                        events.add(event);
                        localIndex++;
                    }
                }
                return MidiTrack.wrap(events.build(), absTick);
            } finally {
                inputs().clear();
            }
        };
    }

    private interface MidiEventConstructor {

        @Nullable
        MidiEvent construct(long index, int tick, int activeChannel, int[] data) throws IOException;

    }

    private static final RangeMap<Integer, Entry<Integer, MidiEventConstructor>> eventCreators;

    static {
        ImmutableRangeMap.Builder<Integer, Entry<Integer, MidiEventConstructor>> b = ImmutableRangeMap.builder();

        // channel messages
        b.put(Range.closed(0x80, 0x8F), Maps.immutableEntry(2, (index, tick, chan, data) -> {
            return NoteOffEvent.create(index, tick, chan, data[0], data[1]);
        }));
        b.put(Range.closed(0x90, 0x9F), Maps.immutableEntry(2, (index, tick, chan, data) -> {
            if (data[1] == 0) {
                return NoteOffEvent.create(index, tick, chan, data[0], data[1]);
            }
            return NoteOnEvent.create(index, tick, chan, data[0], data[1]);
        }));
        b.put(Range.closed(0xA0, 0xAF), Maps.immutableEntry(2, (index, tick, chan, data) -> {
            return NoteAftertouchEvent.create(index, tick, chan, data[0], data[1]);
        }));
        b.put(Range.closed(0xC0, 0xCF), Maps.immutableEntry(1, (index, tick, chan, data) -> {
            return ProgramChangeEvent.create(index, tick, chan, data[0]);
        }));
        b.put(Range.closed(0xD0, 0xDF), Maps.immutableEntry(1, (index, tick, chan, data) -> {
            return ChannelAftertouchEvent.create(index, tick, chan, data[0]);
        }));
        b.put(Range.closed(0xE0, 0xEF), Maps.immutableEntry(2, (index, tick, chan, data) -> {
            // yes, 7, each value is only 7 bits of data
            return PitchBendEvent.create(index, tick, chan, data[0] & (data[1] << 7));
        }));

        eventCreators = b.build();
    }

    // MSB, LSB
    private int[] bankSelect = { -1, -1 };

    @Nullable
    private MidiEvent controlChange(long index, int tick, int chan, int data1, int data2) {
        // I have no clue what * Mode Off is, they all cause AllNotesOffEvent
        // anyways...
        switch (data1) {
            case 0:
                bankSelect[0] = data2;
                break;
            case 32:
                bankSelect[1] = data2;
                break;
            case 123:
            case 124:
            case 125:
            case 126:
            case 127:
                return AllNotesOffEvent.create(index, tick, chan);
            default:
                return ControllerEvent.create(index, tick, chan, data1, data2);
        }
        if (bankSelect[0] != -1 && bankSelect[1] != -1) {
            int bank = bankSelect[1] | (bankSelect[0] << 8);
            bankSelect[0] = bankSelect[1] = -1;
            return BankSelectEvent.create(index, tick, chan, bank);
        }
        return null;
    }

    @Nullable
    private MidiEvent metaEvent(long index, int tick, int channel, int id) throws IOException {
        switch (id) {
            case 0x00:
                return SequenceNumberEvent.create(index, tick, channel, readShort("sequence number"));
            case 0x01:
                return TextEvent.create(index, tick, channel, readText("text"));
            case 0x02:
                return CopyrightNoticeEvent.create(index, tick, channel, readText("copyright notice"));
            case 0x03:
                return TrackNameEvent.create(index, tick, channel, readText("track name"));
            case 0x04:
                return InstrumentNameEvent.create(index, tick, channel, readText("instrument name"));
            case 0x05:
                return LyricEvent.create(index, tick, channel, readText("lyric"));
            case 0x06:
                return MarkerEvent.create(index, tick, channel, readText("marker"));
            case 0x07:
                return CuePointEvent.create(index, tick, channel, readText("cue"));
            case 0x2F:
                return EndOfTrackEvent.create(index, tick, channel);
            case 0x51:
                return SetTempoEvent.create(index, tick, channel, read24BitValue("tempo"));

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

    private void pushLength(int length) throws IOException {
        // inputs.addLast(ByteStreams.limit(input(), length));
        // System.err.println("Push " + input() + " = " + length);
        // On biggest chunks, read in full byte of chunk
        // On subsequent smaller chunks, use existing array with offset
        InputStream in = input();
        byte[] src;
        int off;
        if (in instanceof ExposedByteArrayInputStream) {
            ExposedByteArrayInputStream exposed = (ExposedByteArrayInputStream) in;
            src = exposed.getBuffer();
            off = exposed.getOffset();
        } else {
            src = new byte[length];
            ByteStreams.readFully(in, src);
            off = 0;
        }
        inputs().addLast(new ExposedByteArrayInputStream(src, off, length));
    }

    private void popLength() throws IOException {
        InputStream removed = inputs().removeLast();
        ByteStreams.exhaust(removed);
        InputStream current = input();
        if (removed instanceof ExposedByteArrayInputStream
                && current instanceof ExposedByteArrayInputStream) {
            // update underlying position
            ExposedByteArrayInputStream cExp = (ExposedByteArrayInputStream) current;
            cExp.setOffset(cExp.getOffset() +
                    ((ExposedByteArrayInputStream) removed).getLength());
        }
    }

    // with helper fields here
    private final ThreadLocal<int[]> data1Array = ThreadLocal.withInitial(() -> new int[1]);
    private final ThreadLocal<int[]> data2Array = ThreadLocal.withInitial(() -> new int[2]);
    private final ThreadLocal<byte[]> readSingleArray = ThreadLocal.withInitial(() -> new byte[1]);
    private final ThreadLocal<byte[]> readTagArray = ThreadLocal.withInitial(() -> new byte[4]);
    private final ThreadLocal<byte[]> readShortArray = ThreadLocal.withInitial(() -> new byte[Short.BYTES]);
    private final ThreadLocal<byte[]> read24Array = ThreadLocal.withInitial(() -> new byte[3]);
    private final ThreadLocal<byte[]> readIntArray = ThreadLocal.withInitial(() -> new byte[Integer.BYTES]);

    private void readFully(String description, ThreadLocal<byte[]> arrayTl) throws IOException {
        byte[] array = arrayTl.get();
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
        return readSingleArray.get()[0];
    }

    private short readShort(String description) throws IOException {
        readFully(description, readShortArray);
        return (short) (((readShortArray.get()[0] & 0xFF) << 8) + ((readShortArray.get()[1] & 0xFF)));
    }

    private int read24BitValue(String description) throws IOException {
        readFully(description, read24Array);
        return ((read24Array.get()[0] & 0xFF) << 16)
                + ((read24Array.get()[1] & 0xFF) << 8)
                + ((read24Array.get()[2] & 0xFF));
    }

    private int readInt(String description) throws IOException {
        readFully(description, readIntArray);
        return ((readIntArray.get()[0] & 0xFF) << 24)
                + ((readIntArray.get()[1] & 0xFF) << 16)
                + ((readIntArray.get()[2] & 0xFF) << 8)
                + ((readIntArray.get()[3] & 0xFF));
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
        byte[] array = readTagArray.get();
        checkState(Arrays.equals(array, expectedTag),
                "Not a %s tag: %s", tagType, Arrays.toString(array));
    }

    private String readText(String description) throws IOException {
        // text is just until end of current stream
        return CharStreams.toString(new InputStreamReader(input(), StandardCharsets.US_ASCII));
    }

}
