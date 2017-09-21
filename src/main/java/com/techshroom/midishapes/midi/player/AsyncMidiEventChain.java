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
package com.techshroom.midishapes.midi.player;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.techshroom.midishapes.midi.event.MidiEvent;

class AsyncMidiEventChain implements MidiEventChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncMidiEventChain.class);

    private static final class RunningChain implements MidiEventChain {

        // single threaded to ensure strict ordering
        private static final ExecutorService POOL = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("amec-%d").build());

        private final MidiPlayer player;
        private final Iterator<MidiEventChainLink> chain;
        private volatile MidiEvent event;

        RunningChain(MidiPlayer player, List<MidiEventChainLink> chain) {
            this.player = player;
            this.chain = ImmutableList.copyOf(chain).iterator();
        }

        @Override
        public MidiPlayer player() {
            return player;
        }

        @Override
        public MidiEvent currentEvent() {
            return checkNotNull(event, "running chain not started by parent");
        }

        @Override
        public void sendEventToNext(MidiEvent event) {
            this.event = event;
            if (this.chain.hasNext()) {
                MidiEventChainLink next = this.chain.next();
                CompletableFuture.runAsync(() -> next.onEvent(this), POOL)
                        .whenComplete((res, err) -> {
                            if (err != null) {
                                LOGGER.error("Error in chain link", err);
                            }
                        });
            }
        }

        private void start(MidiEvent event) {
            sendEventToNext(event);
        }

    }

    private final MidiPlayer player;
    private final List<MidiEventChainLink> chain;

    AsyncMidiEventChain(MidiPlayer player, List<MidiEventChainLink> chain) {
        this.player = player;
        this.chain = ImmutableList.copyOf(chain);
    }

    @Override
    public MidiPlayer player() {
        return player;
    }

    @Override
    public MidiEvent currentEvent() {
        throw new IllegalStateException("In-active chain.");
    }

    @Override
    public void sendEventToNext(MidiEvent event) {
        new RunningChain(player, chain).start(event);
    }

}
