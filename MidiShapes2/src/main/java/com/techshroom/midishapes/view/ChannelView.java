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
package com.techshroom.midishapes.view;

import static com.google.common.base.Preconditions.checkState;
import static com.techshroom.midishapes.view.ViewComponents.OFFSETS;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.techshroom.midishapes.midi.MidiFile;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.StartEvent;
import com.techshroom.midishapes.midi.event.StopEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;
import com.techshroom.midishapes.midi.player.MidiEventChain;
import com.techshroom.midishapes.midi.player.MidiEventChainLink;
import com.techshroom.midishapes.midi.player.MidiPlayer;
import com.techshroom.unplanned.blitter.Drawable;
import com.techshroom.unplanned.blitter.GraphicsContext;
import com.techshroom.unplanned.blitter.Shape;
import com.techshroom.unplanned.blitter.binding.Bindable;
import com.techshroom.unplanned.blitter.shapers.CubeLayout;
import com.techshroom.unplanned.blitter.textures.Downscaling;
import com.techshroom.unplanned.blitter.textures.Texture;
import com.techshroom.unplanned.blitter.textures.TextureSettings;
import com.techshroom.unplanned.blitter.textures.TextureWrap;
import com.techshroom.unplanned.blitter.textures.Upscaling;
import com.techshroom.unplanned.blitter.textures.loader.ColorTextureSpec;
import com.techshroom.unplanned.blitter.textures.loader.StandardTextureLoaders;
import com.techshroom.unplanned.blitter.transform.TransformStack;
import com.techshroom.unplanned.core.util.LifecycleObject;
import com.techshroom.unplanned.core.util.Maths;

/**
 * Draws the note stream for a channel.
 */
final class ChannelView implements Drawable, LifecycleObject, MidiEventChainLink {

    private static final ThreadLocal<Random> RANDOM = ThreadLocal.withInitial(Random::new);

    private static final int PIXELS_PER_TICK = 1;
    private static final double NOTE_WIDTH = 15 / PIXELS_PER_TICK;
    private static final double NOTE_DEPTH = 5 / PIXELS_PER_TICK;

    private final ImmutableList<MidiEvent> track;
    private final Deque<Shape> notes = new LinkedList<>();
    private final GraphicsContext ctx;
    private final MidiFile src;
    private final Lock millisLock = new ReentrantLock();
    private final MidiPlayer player;
    private final int channel;
    private int eventIndex;
    private Texture color;
    private boolean unsetMillisBase = true;
    private long millisBase;

    public ChannelView(int channel, GraphicsContext ctx, MidiFile src, MidiPlayer player) {
        this.channel = channel;
        this.ctx = ctx;
        this.src = src;
        this.player = player;
        this.track = src.getChannelTracks().get(channel);
    }

    private Vector3i randomColor() {
        Random r = RANDOM.get();
        r.setSeed(channel);
        return new Vector3i(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    @Override
    public void initialize() {
        color = ctx.getTextureProvider().load(
                StandardTextureLoaders.RGBA_COLOR_LOADER.load(
                        ColorTextureSpec.create(randomColor(), 1, 1)),
                TextureSettings.builder()
                        .downscaling(Downscaling.NEAREST)
                        .upscaling(Upscaling.NEAREST)
                        .textureWrapping(TextureWrap.REPEAT)
                        .build());

        int[] noteTicks = new int[PianoView.PIANO_SIZE];
        for (MidiEvent event : track) {
            if (event instanceof NoteOnEvent) {
                noteTicks[((NoteOnEvent) event).getNote()] = event.getTick();
            }
            if (event instanceof NoteOffEvent) {
                int note = ((NoteOffEvent) event).getNote();
                if (noteTicks[note] == 0) {
                    continue;
                }
                Vector3d offset = OFFSETS[note].toDouble();
                Vector3d v1 = new Vector3d(0, noteTicks[note], 0).mul(PIXELS_PER_TICK);
                Vector3d v2 = new Vector3d(NOTE_WIDTH, event.getTick(), NOTE_DEPTH).mul(PIXELS_PER_TICK);
                notes.add(ctx.getShapes().rectPrism().shape(
                        offset.add(v1),
                        offset.add(v2),
                        CubeLayout.singleTexture()));
                noteTicks[note] = 0;
            }
        }
        notes.forEach(Shape::initialize);

        millisBase = player.getCurrentMillis();
    }

    @Override
    public void destroy() {
        notes.forEach(Shape::destroy);
        notes.clear();
    }

    private long ms(int tick) {
        return src.getTimingData().getMillisecondOffset(tick);
    }

    @Override
    public void onEvent(MidiEventChain chain) {
        boolean start = chain.currentEvent() instanceof StartEvent;
        boolean stop = chain.currentEvent() instanceof StopEvent;
        if (start || stop || chain.currentEvent().getChannel() == channel) {
            millisLock.lock();
            try {
                if (start) {
                    int startTick = chain.currentEvent().getTick();
                    // track is composed of all events on or after start tick
                    eventIndex = Iterables.indexOf(track, e -> e.getTick() >= startTick) - 1;
                    millisBase = ((StartEvent) chain.currentEvent()).getStartMillis();
                    unsetMillisBase = false;
                } else if (stop) {
                    eventIndex = 0;
                    millisBase = 0;
                    unsetMillisBase = true;
                } else {
                    eventIndex++;
                    checkState(track.get(eventIndex).equals(chain.currentEvent()), "track out of sync with pumper");
                }
            } finally {
                millisLock.unlock();
            }
        }
        chain.sendCurrentEventToNext();
    }

    @Override
    public void draw() {
        final float offset;
        millisLock.lock();
        try {
            if (!unsetMillisBase && eventIndex >= 0) {
                final long offsetM = player.getCurrentMillis() - millisBase;

                final int endMsIndex = (eventIndex + 1) >= track.size() ? 0 : 1;
                final int tickFrom = track.get(eventIndex).getTick();
                final int tickTo = track.get(eventIndex + endMsIndex).getTick();
                final long startMs = ms(tickFrom);
                final long endMs = ms(tickTo);

                final float millisDiff = endMs - startMs;
                final float progressFactor = (offsetM - startMs) / millisDiff;
                offset = Maths.lerp(tickFrom, tickTo, progressFactor);
            } else {
                offset = 0;
            }
        } finally {
            millisLock.unlock();
        }
        try (TransformStack stack = ctx.pushTransformer(); Bindable tex = color.bind()) {
            stack.model()
                    .translate(0, -PIXELS_PER_TICK * offset, 0);
            stack.apply(ctx.getMatrixUploader());
            notes.forEach(Shape::draw);
        }
    }

}
