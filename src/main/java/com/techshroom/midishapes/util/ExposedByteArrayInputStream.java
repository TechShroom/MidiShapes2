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

package com.techshroom.midishapes.util;

import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayInputStream;

/**
 * Exposes byte array of {@link ByteArrayInputStream}.
 */
public class ExposedByteArrayInputStream extends ByteArrayInputStream {

    private final int inLength;

    public ExposedByteArrayInputStream(byte[] buf, int offset, int length) {
        super(buf, offset, length);
        checkState(offset + length <= buf.length, "offset (%s) + length (%s) is too large! (>%s)", offset, length, buf.length);
        inLength = length;
    }

    public ExposedByteArrayInputStream(byte[] buf) {
        this(buf, 0, buf.length);
    }

    public int getLength() {
        return inLength;
    }

    public byte[] getBuffer() {
        return buf;
    }

    public int getOffset() {
        return pos;
    }

    public void setOffset(int offset) {
        pos = offset;
    }

}
