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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.techshroom.midishapes.view.ViewComponents.BLACK_NOTE_LENGTH;
import static com.techshroom.midishapes.view.ViewComponents.BLACK_NOTE_WIDTH;
import static com.techshroom.midishapes.view.ViewComponents.OFFSETS;
import static com.techshroom.midishapes.view.ViewComponents.PIANO_SIZE;
import static com.techshroom.midishapes.view.ViewComponents.WHITE_NOTE_LENGTH;
import static com.techshroom.midishapes.view.ViewComponents.WHITE_NOTE_WIDTH;
import static com.techshroom.midishapes.view.ViewComponents.isWhiteKey;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.techshroom.midishapes.midi.MidiFile;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.StartEvent;
import com.techshroom.midishapes.midi.event.StopEvent;
import com.techshroom.midishapes.midi.event.channel.AllNotesOffEvent;
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
    private static final int RANDOM_SEED_OFFSET = (int) (Math.random() * 1000000);

    private static final int MAXIMUM_LOADED_SHAPES = 2000;
    private static final int MAX_POST_KEY = MAXIMUM_LOADED_SHAPES / 2;

    private static final int PIXELS_PER_TICK = 1;
    private static final double NOTE_WIDTH = 15 / PIXELS_PER_TICK;
    private static final double NOTE_DEPTH = 5 / PIXELS_PER_TICK;

    private final ImmutableList<MidiEvent> track;
    private final Lock notesLock = new ReentrantLock();
    private final Deque<Entry<MidiEvent, Shape>> notes = new LinkedList<>();
    private final Set<MidiEvent> ackedEvents = new HashSet<>();
    private final Deque<MidiEvent> hitNotes = new LinkedList<>();
    private final Set<MidiEvent> skippedEvents = new HashSet<>();
    private final GraphicsContext ctx;
    private final MidiFile src;
    private final Lock millisLock = new ReentrantLock();
    private final MidiPlayer player;
    private final int channel;
    private final AtomicIntegerArray noteTicks = new AtomicIntegerArray(PIANO_SIZE);
    private int unloadedStart;
    private int eventIndex;
    private Texture color;
    private boolean unsetMillisBase = true;
    private boolean startReinit = false;
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
        r.setSeed(channel + RANDOM_SEED_OFFSET);
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

        clearNoteState();
        addNotesUntilPreHit();

        millisBase = player.getCurrentMillis();
    }

    private void addNotesUntilPreHit() {
        for (; unloadedStart < track.size(); unloadedStart++) {
            // PHME is at max MAX_POST_KEY
            // leaving the rest to be <= MAX_PRE_KEY
            MidiEvent event = track.get(unloadedStart);
            if (event instanceof NoteOnEvent) {
                noteTicks.set(((NoteOnEvent) event).getNote(), event.getTick());
            }
            if (event instanceof NoteOffEvent) {
                int note = ((NoteOffEvent) event).getNote();
                if (onNoteOff(event, note)) {
                    break;
                }
            }
            if (event instanceof AllNotesOffEvent) {
                for (int i = 0; i < PIANO_SIZE; i++) {
                    if (onNoteOff(event, i)) {
                        // reset unloadedStart to fire AllNotesOff again later
                        unloadedStart--;
                        break;
                    }
                }
            }
        }
    }

    private boolean onNoteOff(MidiEvent event, int note) {
        int lastTick = noteTicks.get(note);
        if (lastTick == 0) {
            return false;
        }
        Vector3d offset = OFFSETS[note].toDouble();
        if (isWhiteKey(note)) {
            offset = offset.add((WHITE_NOTE_WIDTH - NOTE_WIDTH) / 2.0, 0, WHITE_NOTE_LENGTH - 10);
        } else {
            offset = offset.add((BLACK_NOTE_WIDTH - NOTE_WIDTH) / 2.0, 0, BLACK_NOTE_LENGTH - 10);
        }
        Vector3d v1 = new Vector3d(0, lastTick, 0).mul(PIXELS_PER_TICK);
        Vector3d v2 = new Vector3d(NOTE_WIDTH, event.getTick(), NOTE_DEPTH).mul(PIXELS_PER_TICK);
        Shape shape = ctx.getShapes().rectPrism().shape(
                offset.add(v1),
                offset.add(v2),
                CubeLayout.singleTexture());
        shape.initialize();
        notesLock.lock();
        try {
            // we can add more notes if there are some yet to be hit
            if ((notes.size() - hitNotes.size()) >= MAXIMUM_LOADED_SHAPES) {
                return true;
            }
            ackedEvents.add(event);
            notes.add(Maps.immutableEntry(event, shape));
        } finally {
            notesLock.unlock();
        }
        noteTicks.set(note, 0);
        return false;
    }

    @Override
    public void destroy() {
        clearNoteState();
    }

    private void clearNoteState() {
        notesLock.lock();
        try {
            unloadedStart = 0;
            ackedEvents.clear();
            skippedEvents.clear();
            hitNotes.clear();
            notes.forEach(e -> e.getValue().destroy());
            notes.clear();
        } finally {
            notesLock.unlock();
        }
    }

    private long ms(int tick) {
        return src.getTimingData().getMillisecondOffset(tick);
    }

    @Override
    public void onEvent(MidiEventChain chain) {
        MidiEvent event = chain.currentEvent();
        boolean start = event instanceof StartEvent;
        boolean stop = event instanceof StopEvent;
        if (start || stop || event.getChannel() == channel) {
            millisLock.lock();
            try {
                if (start) {
                    int startTick = event.getTick();
                    // track is composed of all events on or after start tick
                    eventIndex = Iterables.indexOf(track, e -> e.getTick() >= startTick) - 1;
                    millisBase = ((StartEvent) event).getStartMillis();
                    unsetMillisBase = false;
                    startReinit = true;
                } else if (stop) {
                    eventIndex = 0;
                    millisBase = 0;
                    unsetMillisBase = true;
                } else {
                    eventIndex++;
                    if (!track.get(eventIndex).equals(event)) {
                        System.err.println("mismatch detected in this block; index=" + eventIndex);
                        System.err.println("the expected index should have been...." + track.indexOf(event));
                        System.err.println(track.get(eventIndex) + " != " + event);
                    }
                    checkState(track.get(eventIndex).equals(event), "track out of sync with pumper");
                    notesLock.lock();
                    try {
                        if (ackedEvents.contains(event)) {
                            // push note to POST stage, we should be in sync
                            hitNotes.add(event);
                        } else if (event instanceof NoteOffEvent || event instanceof AllNotesOffEvent) {
                            skippedEvents.add(event);
                        }
                    } finally {
                        notesLock.unlock();
                    }
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

            notesLock.lock();
            try {
                if (startReinit) {
                    clearNoteState();
                    addNotesUntilPreHit();
                    startReinit = false;
                }
                while (hitNotes.size() > MAX_POST_KEY) {
                    MidiEvent nextHit = hitNotes.removeFirst();

                    // remove extra skipped events
                    while (skippedEvents.contains(notes.getFirst().getKey())) {
                        Entry<MidiEvent, Shape> removed = notes.remove();
                        ackedEvents.remove(removed.getKey());
                        removed.getValue().destroy();
                        skippedEvents.remove(removed.getKey());
                        addNotesUntilPreHit();
                    }

                    if (notes.getFirst().getKey().equals(nextHit)) {
                        Entry<MidiEvent, Shape> removed = notes.remove();
                        ackedEvents.remove(removed.getKey());
                        removed.getValue().destroy();
                    } else {
                        System.err.println(notes.stream().map(Entry::getKey).collect(toImmutableList()));
                        System.err.println(hitNotes);
                        checkState(false, "should have removed a hit note: hit=%s, top=%s", nextHit, notes.getFirst().getKey());
                    }
                }
                addNotesUntilPreHit();
            } finally {
                notesLock.unlock();
            }
        } finally {
            millisLock.unlock();
        }
        try (TransformStack stack = ctx.pushTransformer(); Bindable tex = color.bind()) {
            stack.model()
                    .translate(0, -PIXELS_PER_TICK * offset, 0);
            stack.apply(ctx.getMatrixUploader());
            notes.forEach(e -> e.getValue().draw());
        }
    }

}
