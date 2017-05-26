package com.techshroom.midishapes.midi.player;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techshroom.midishapes.midi.MidiTiming;
import com.techshroom.midishapes.midi.event.MidiEvent;

class MidiEngine implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiEngine.class);

    private volatile boolean running;
    private final Lock runningLock = new ReentrantLock();
    private final Condition runningCondition = runningLock.newCondition();
    private final AtomicReference<MidiSoundfont> sounds = new AtomicReference<>();
    private final AtomicReference<MidiTiming> timing = new AtomicReference<>();
    private final AtomicReference<Iterator<MidiEvent>> stream = new AtomicReference<>();
    private final AtomicReference<Consumer<Object>> registrationFunction = new AtomicReference<>();

    void start(MidiTiming timing, MidiSoundfont sounds, Iterator<MidiEvent> stream) {
        this.timing.set(timing);
        this.sounds.set(sounds);
        this.stream.set(stream);
        setRunning(true);
    }

    void stop() {
        setRunning(false);
        sounds.set(null);
        timing.set(null);
        stream.set(null);
    }

    void addHandler(Object object) {
        checkNotNull(registrationFunction.get(), "cannot register now").accept(object);
    }

    private void setRunning(boolean running) {
        runningLock.lock();
        try {
            this.running = running;
            runningCondition.signal();
        } finally {
            runningLock.unlock();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                runningLock.lock();
                try {
                    while (!this.running) {
                        runningCondition.await();
                    }
                } finally {
                    runningLock.unlock();
                }

                try {
                    playMidiStream();
                } catch (EarlyReturnError returned) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                }
            } catch (InterruptedException e) {
                // time to die!
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.warn("error in MIDI stream player", e);
            }
        }
    }

    /**
     * Check if the thread should return to waiting to run.
     */
    private void checkIfShouldReturn() {
        if (!running || Thread.interrupted()) {
            throw EarlyReturnError.getInstance();
        }
    }

    private void playMidiStream() {
        // save stream to improve performance
        final Iterator<MidiEvent> stream = this.stream.get();
        final MidiTiming timing = this.timing.get();
        final MidiState state = new MidiState(this.sounds.get());
        final long startMillis = accurateMilliseconds();

        registrationFunction.set(state::addHandler);

        while (stream.hasNext()) {
            checkIfShouldReturn();
            MidiEvent next = stream.next();
            waitForEvent(next.getTick(), timing, startMillis);
            checkIfShouldReturn();
            state.onEvent(next);
        }
    }

    private long accurateMilliseconds() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void waitForEvent(int tick, MidiTiming timing, long startMillis) {
        long eventMillis = timing.getMillisecondOffset(tick) + startMillis;
        long millisDiff = eventMillis - accurateMilliseconds();
        if (millisDiff > 0) {
            // wait until about 5ms before, then churn to be accurate
            long wait = millisDiff - 5;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    throw EarlyReturnError.getInstance();
                }
            }
            while ((eventMillis - accurateMilliseconds()) > 0) {
                // churn
            }
        }
    }

}
