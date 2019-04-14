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

import java.nio.file.Path;

public interface MidiSoundPlayer extends MidiEventChainLink, AutoCloseable {

    static MidiSoundPlayer getDefault() {
        return JavaxSoundPlayer.getInstance();
    }

    /**
     * Called upon when the model wants settings changed for the player.
     */
    void openSettingsPanel();

    /**
     * Called upon when the model has a new SF2 to hook up.
     * 
     * @param sf2File
     *            the file to use as the soundfont
     */
    void setSoundfont(Path sf2File);

    MidiSoundPlayer open();

    @Override
    void close();

}
