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
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;

import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.channel.ChannelEvent;
import com.techshroom.midishapes.midi.event.encode.MidiEventEncoder;

final class JavaxSoundPlayer implements MidiSoundPlayer {

    private static final JavaxSoundPlayer INSTANCE = new JavaxSoundPlayer();

    static JavaxSoundPlayer getInstance() {
        return INSTANCE;
    }

    private final Lock lock = new ReentrantLock();
    private MidiEventEncoder enc;
    private Receiver target;

    private JavaxSoundPlayer() {
    }

    @Override
    public JavaxSoundPlayer open() {
        lock.lock();
        try {
            target = MidiSystem.getReceiver();
            checkNotNull(target, "no receiver opened!");
            enc = MidiEventEncoder.getInstance();
        } catch (MidiUnavailableException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
        return this;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            enc = null;
            if (target != null) {
                target.close();
                target = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private static final class SimpleMessage extends MidiMessage {

        protected SimpleMessage(byte[] data) {
            super(data);
        }

        @Override
        public Object clone() {
            return new SimpleMessage(data);
        }

    }

    @Override
    public void onEvent(MidiEventChain chain) {
        MidiEvent e = chain.currentEvent();
        if (e instanceof ChannelEvent) {
            handleEveryEvent((ChannelEvent) e);
        }
        chain.sendCurrentEventToNext();
    }

    private void handleEveryEvent(ChannelEvent event) {
        lock.lock();
        try {
            checkState(target != null, "not opened");
            target.send(new SimpleMessage(enc.encode(event)), -1);
        } finally {
            lock.unlock();
        }
    }

}
