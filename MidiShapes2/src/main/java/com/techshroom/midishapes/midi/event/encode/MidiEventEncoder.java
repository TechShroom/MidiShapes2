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
package com.techshroom.midishapes.midi.event.encode;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
import com.techshroom.midishapes.midi.event.channel.ChannelAftertouchEvent;
import com.techshroom.midishapes.midi.event.channel.ChannelEvent;
import com.techshroom.midishapes.midi.event.channel.ControllerEvent;
import com.techshroom.midishapes.midi.event.channel.NoteAftertouchEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;
import com.techshroom.midishapes.midi.event.channel.PitchBendEvent;
import com.techshroom.midishapes.midi.event.channel.ProgramChangeEvent;

/**
 * Encodes {@link MidiEvent} as bytes.
 */
public final class MidiEventEncoder {

    private static final ThreadLocal<MidiEventEncoder> INSTANCE = ThreadLocal.withInitial(MidiEventEncoder::new);

    public static MidiEventEncoder getInstance() {
        return INSTANCE.get();
    }

    private static final Map<Class<?>, Encoder<? extends MidiEvent>> encoders;
    static {
        ImmutableMap.Builder<Class<?>, Encoder<? extends MidiEvent>> b = ImmutableMap.builder();

        b.put(NoteOffEvent.class, Encoder.twoByte(0x80, NoteOffEvent::getNote, NoteOffEvent::getVelocity));
        b.put(NoteOnEvent.class, Encoder.twoByte(0x90, NoteOnEvent::getNote, NoteOnEvent::getVelocity));
        b.put(NoteAftertouchEvent.class, Encoder.twoByte(0xA0, NoteAftertouchEvent::getNote, NoteAftertouchEvent::getAftertouch));
        b.put(AllNotesOffEvent.class, Encoder.twoByte(0xB0, e -> 123, e -> 0));
        b.put(ControllerEvent.class, Encoder.twoByte(0xB0, ControllerEvent::getController, ControllerEvent::getValue));
        b.put(ProgramChangeEvent.class, Encoder.oneByte(0xC0, ProgramChangeEvent::getProgram));
        b.put(ChannelAftertouchEvent.class, Encoder.oneByte(0xD0, ChannelAftertouchEvent::getValue));
        b.put(PitchBendEvent.class, Encoder.<PitchBendEvent> twoByte(0xE0,
                e -> e.getPitch() & 0x7f,
                e -> (e.getPitch() >> 7) & 0x7f));

        encoders = b.build();
    }

    // OPTIMIZATION for encoding one event as a byte[]
    private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    // OPTIMIZATION for repeated calls with same stream
    // i.e. encoding a list, or stream of events
    private OutputStream activeStream;
    private DataOutputStream activeDataStream;

    private MidiEventEncoder() {
    }

    public byte[] encode(MidiEvent event) {
        capture.reset();
        try {
            encode(event, capture);
        } catch (IOException e) {
            // impossible, in-memory stream
            throw new AssertionError("ByteArrayOutputStream threw IOException", e);
        }
        return capture.toByteArray();
    }

    public void encode(MidiEvent event, OutputStream stream) throws IOException {
        checkArgument(event instanceof ChannelEvent, "only channel events can be encoded right now");
        checkNotNull(stream, "stream");
        if (activeStream != stream) {
            activeDataStream = new DataOutputStream(stream);
            activeStream = stream;
        }

        // java is really dumb
        @SuppressWarnings("unchecked")
        Encoder<MidiEvent> encoder = (Encoder<MidiEvent>) getEncoder(event.getClass());
        checkNotNull(encoder, "missing encoder for %s", event.getClass());
        encoder.encode(event, activeDataStream);
    }

    private Encoder<? extends MidiEvent> getEncoder(Class<? extends MidiEvent> c) {
        Deque<Class<?>> classes = new LinkedList<>();
        classes.addLast(c);
        while (!classes.isEmpty()) {
            Class<?> current = classes.removeFirst();
            Encoder<? extends MidiEvent> enc = encoders.get(current);
            if (enc != null) {
                return enc;
            }

            classes.addLast(current.getSuperclass());
            Stream.of(current.getInterfaces()).forEach(classes::addLast);
        }
        return null;
    }

    public void finishUsing() {
        activeStream = activeDataStream = null;
    }

}
