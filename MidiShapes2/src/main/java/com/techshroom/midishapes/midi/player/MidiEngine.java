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

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.techshroom.midishapes.midi.MidiTiming;
import com.techshroom.midishapes.midi.event.MidiEvent;

class MidiEngine implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiEngine.class);

    private final Thread thread = new Thread(this, "MidiEngine");
    private volatile boolean running;
    private final ReadWriteLock runningLock = new ReentrantReadWriteLock();
    private final Condition runningCondition = runningLock.writeLock().newCondition();
    private final AtomicReference<MidiSoundPlayer> sounds = new AtomicReference<>();
    private final AtomicReference<MidiTiming> timing = new AtomicReference<>();
    private final AtomicReference<Iterator<MidiEvent>> stream = new AtomicReference<>();
    private final AtomicReference<Set<Object>> midiEventListeners = new AtomicReference<>();
    private final AtomicReference<EventBus> events = new AtomicReference<>();

    MidiEngine() {
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void start(MidiTiming timing, MidiSoundPlayer sounds, Iterator<MidiEvent> stream, Set<Object> listeners) {
        this.midiEventListeners.set(listeners);
        EventBus eventBus = new EventBus("midi-engine");
        listeners.forEach(eventBus::register);
        this.events.set(eventBus);
        this.timing.set(timing);
        this.sounds.set(sounds);
        this.stream.set(stream);
        setRunning(true);
    }

    void stop() {
        Set<Object> listeners = midiEventListeners.get();
        EventBus eventBus = events.get();
        if (listeners != null && eventBus != null) {
            listeners.forEach(eventBus::unregister);
        }
        setRunning(false);
        thread.interrupt();
        sounds.set(null);
        timing.set(null);
        stream.set(null);
        events.set(null);
        midiEventListeners.set(null);
    }

    private void setRunning(boolean running) {
        runningLock.writeLock().lock();
        try {
            this.running = running;
            runningCondition.signal();
        } finally {
            runningLock.writeLock().unlock();
        }
    }

    private boolean isRunning() {
        runningLock.readLock().lock();
        try {
            return running;
        } finally {
            runningLock.readLock().unlock();
        }
    }

    private void awaitRunning() throws InterruptedException {
        if (!this.running) {
            runningLock.writeLock().lock();
            try {
                while (!this.running) {
                    runningCondition.await();
                }
            } finally {
                runningLock.writeLock().unlock();
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                awaitRunning();

                try {
                    playMidiStream();
                } catch (EarlyReturnError returned) {
                    // thread may be interrupted to stop a running track
                    if (running && Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                }
            } catch (InterruptedException e) {
                // time to die!
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.warn("error in MIDI stream player", e);
            } finally {
                stop();
            }
        }
    }

    /**
     * Check if the thread should return to waiting to run.
     */
    private void checkIfShouldReturn() {
        if (!isRunning() || Thread.interrupted()) {
            throw EarlyReturnError.getInstance();
        }
    }

    private void playMidiStream() {
        try (final MidiSoundPlayer sounds = this.sounds.get().open()) {

            // save stream to improve performance
            final Iterator<MidiEvent> stream = this.stream.get();
            final MidiTiming timing = this.timing.get();
            final MidiState state = new MidiState(sounds, events.get());
            final long startMillis = accurateMilliseconds();

            while (stream.hasNext()) {
                checkIfShouldReturn();
                MidiEvent next = stream.next();
                waitForEvent(next.getTick(), timing, startMillis);
                checkIfShouldReturn();
                state.onEvent(next);
            }
        }
    }

    private long accurateMilliseconds() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void waitForEvent(int tick, MidiTiming timing, long startMillis) {
        long eventMillis = timing.getMillisecondOffset(tick) + startMillis;
        long millisDiff = eventMillis - accurateMilliseconds();
        if (millisDiff > 0) {
            // wait, then churn to be accurate
            try {
                Thread.sleep(millisDiff);
            } catch (InterruptedException e) {
                throw EarlyReturnError.getInstance();
            }
            while ((eventMillis - accurateMilliseconds()) > 0) {
                // churn
            }
        }
    }

}
