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

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Consumer;

import com.techshroom.midishapes.midi.event.MidiEvent;

public final class MidiEventHandlerMap {

    private final Map<Class<?>, Consumer<?>> map = new HashMap<>();

    private Consumer<MidiEvent> uncheckedGet(Class<?> key) {
        @SuppressWarnings("unchecked")
        final Consumer<MidiEvent> cons = (Consumer<MidiEvent>) map.get(key);
        return cons;
    }

    public <ME extends MidiEvent> Consumer<MidiEvent> get(Class<ME> key) {
        Deque<Class<?>> classes = new LinkedList<>();
        classes.addFirst(key);
        while (!classes.isEmpty()) {
            Class<?> c = classes.removeFirst();
            if (c == null || !MidiEvent.class.isAssignableFrom(c)) {
                continue;
            }

            Consumer<MidiEvent> cons = uncheckedGet(c);
            if (cons != null) {
                return cons;
            }

            classes.add(c.getSuperclass());
            Collections.addAll(classes, c.getInterfaces());
        }
        return null;
    }

    public <ME extends MidiEvent> void put(Class<ME> key, Consumer<ME> value) {
        map.put(key, value);
    }

}
