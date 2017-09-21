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
package com.techshroom.midishapes.view;

import java.util.List;

import com.flowpowered.math.vector.Vector3f;
import com.techshroom.midishapes.fx.ConcatObservableList;
import com.techshroom.midishapes.midi.player.MidiEventChainLink;

import javafx.beans.binding.ListExpression;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class ViewComponents {

    static final int PIANO_SIZE = 128;

    static final int WHITE_NOTE_WIDTH = 30;
    static final int WHITE_NOTE_HEIGHT = 30;
    static final int WHITE_NOTE_DEPTH = 150;
    static final int BLACK_NOTE_WIDTH = 20;
    static final int BLACK_NOTE_HEIGHT = 20;
    static final int BLACK_NOTE_DEPTH = 50;

    static final int PIANO_DY = 75;
    static final int PIANO_DZ = -175;

    static final int SPACING = 5;

    static final int KEY_SPACING = WHITE_NOTE_WIDTH + SPACING;

    // 7 per octave * 11 octaves - 2 (greater than 127)
    static final int WHITE_KEY_COUNT = 77 - 2;
    static final int PIANO_WIDTH = KEY_SPACING * WHITE_KEY_COUNT;

    static boolean isWhiteKey(int key) {
        switch (key % 12) {
            case 0:
            case 2:
            case 4:
            case 5:
            case 7:
            case 9:
            case 11:
                return true;
        }
        return false;
    }

    static final Vector3f[] OFFSETS = new Vector3f[PIANO_SIZE];
    static {
        int lastWhiteKey = 0;
        int whiteKeyCounter = 0;
        for (int i = 0; i < PIANO_SIZE; i++) {
            if (isWhiteKey(i)) {
                lastWhiteKey = whiteKeyCounter * KEY_SPACING;
                OFFSETS[i] = new Vector3f(lastWhiteKey, 0, 0);
                whiteKeyCounter++;
            } else {
                // size of the black/white key overlap
                float overlapSize = (BLACK_NOTE_WIDTH - SPACING);
                // xOffset is WIDTH - half of overlap
                float xOffset = WHITE_NOTE_WIDTH - (overlapSize / 2f);
                OFFSETS[i] = new Vector3f(lastWhiteKey + xOffset, 20, 0);
            }
        }
    }

    final ObservableList<PianoView> pianos = FXCollections.observableArrayList();
    final ObservableList<ChannelView> channels = FXCollections.observableArrayList();
    private final ListProperty<MidiEventChainLink> chainExp = new ReadOnlyListWrapper<>(new ConcatObservableList<>(channels, pianos));

    public ListExpression<MidiEventChainLink> viewEventChainExpression() {
        return chainExp;
    }

    public List<MidiEventChainLink> getViewEventChain() {
        return chainExp.get();
    }

}
