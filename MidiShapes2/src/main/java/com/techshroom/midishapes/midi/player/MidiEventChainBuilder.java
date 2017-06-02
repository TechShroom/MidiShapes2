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

import com.google.common.collect.ImmutableList;

public final class MidiEventChainBuilder {

    private final MidiPlayer player;
    private boolean async = false;
    private final ImmutableList.Builder<MidiEventChainLink> links = ImmutableList.builder();

    MidiEventChainBuilder(MidiPlayer player) {
        this.player = player;
    }

    public MidiEventChainBuilder async() {
        return async(true);
    }

    public MidiEventChainBuilder sync() {
        return async(false);
    }

    public MidiEventChainBuilder async(boolean async) {
        this.async = async;
        return this;
    }

    public MidiEventChainBuilder add(MidiEventChainLink element) {
        links.add(element);
        return this;
    }

    public MidiEventChainBuilder addAll(Iterable<? extends MidiEventChainLink> elements) {
        links.addAll(elements);
        return this;
    }

    public MidiEventChainBuilder add(MidiEventChainLink... elements) {
        links.add(elements);
        return this;
    }

    public MidiEventChain build() {
        ImmutableList<MidiEventChainLink> c = links.build();
        return async ? new AsyncMidiEventChain(player, c) : new SyncMidiEventChain(player, c);
    }

}
