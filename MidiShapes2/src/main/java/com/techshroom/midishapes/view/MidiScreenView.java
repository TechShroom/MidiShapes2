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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import com.flowpowered.math.imaginary.Quaternionf;
import com.flowpowered.math.matrix.Matrix4f;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3f;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.eventbus.Subscribe;
import com.techshroom.midishapes.MidiScreenModel;
import com.techshroom.midishapes.midi.player.MidiPlayer;
import com.techshroom.unplanned.blitter.BindableShape;
import com.techshroom.unplanned.blitter.Drawable;
import com.techshroom.unplanned.blitter.GraphicsContext;
import com.techshroom.unplanned.blitter.binding.Bindable;
import com.techshroom.unplanned.blitter.binding.BindableDrawable;
import com.techshroom.unplanned.blitter.shapers.CubeLayout;
import com.techshroom.unplanned.blitter.textures.Downscaling;
import com.techshroom.unplanned.blitter.textures.Texture;
import com.techshroom.unplanned.blitter.textures.TextureData;
import com.techshroom.unplanned.blitter.textures.TextureSettings;
import com.techshroom.unplanned.blitter.textures.TextureWrap;
import com.techshroom.unplanned.blitter.textures.Upscaling;
import com.techshroom.unplanned.blitter.textures.loader.ColorTextureLoader;
import com.techshroom.unplanned.blitter.textures.loader.ColorTextureSpec;
import com.techshroom.unplanned.blitter.textures.loader.StandardTextureLoaders;
import com.techshroom.unplanned.blitter.textures.map.TextureAtlas;
import com.techshroom.unplanned.blitter.textures.map.TextureCollection;
import com.techshroom.unplanned.core.util.LifecycleObject;
import com.techshroom.unplanned.event.keyboard.KeyState;
import com.techshroom.unplanned.event.keyboard.KeyStateEvent;
import com.techshroom.unplanned.event.window.WindowResizeEvent;
import com.techshroom.unplanned.input.Key;
import com.techshroom.unplanned.window.Window;

public class MidiScreenView implements Drawable, LifecycleObject {

    private static final int PIANO_SIZE = 128;

    private static final int WHITE_NOTE_WIDTH = 30;
    private static final int WHITE_NOTE_LENGTH = 30;
    private static final int WHITE_NOTE_HEIGHT = 150;
    private static final int BLACK_NOTE_WIDTH = 20;
    private static final int BLACK_NOTE_LENGTH = 20;
    private static final int BLACK_NOTE_HEIGHT = 50;

    private static final int PIANO_DY = 75;
    private static final int PIANO_DZ = -175;

    private static final int SPACING = 5;

    // 7 per octave * 11 octaves - 2 (greater than 127)
    private static final int WHITE_KEY_COUNT = 77 - 2;
    private static final int PIANO_WIDTH = (WHITE_NOTE_WIDTH + SPACING) * WHITE_KEY_COUNT;

    private static boolean isWhiteKey(int key) {
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

    private static final Vector3f[] OFFSETS = new Vector3f[PIANO_SIZE];
    static {
        int lastWhiteKey = 0;
        int whiteKeyCounter = 0;
        for (int i = 0; i < PIANO_SIZE; i++) {
            if (isWhiteKey(i)) {
                lastWhiteKey = whiteKeyCounter * (WHITE_NOTE_WIDTH + SPACING);
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

    private final Window window;
    private final GraphicsContext ctx;
    private final MidiScreenModel model;
    private final MidiPlayer player;
    private final List<PianoView> pianos = new ArrayList<>();

    private Vector3f eulerAngles = new Vector3f(-15, 0, 0);
    private Matrix4f projection;
    private Matrix4f view;
    private Texture atlas;
    private BindableShape whiteNote;
    private BindableShape blackNote;
    private BindableShape playingWhiteNote;
    private BindableShape playingBlackNote;

    @Inject
    MidiScreenView(Window window, MidiScreenModel model, MidiPlayer player) {
        this.window = window;
        this.ctx = window.getGraphicsContext();
        this.model = model;
        this.player = player;

        Vector2i size = window.getSize();
        setProjection(size.getX(), size.getY());
        ctx.setLight(new Vector3d(0, 10000, 5000), Vector3d.ONE);
    }

    @Override
    public void initialize() {
        this.model.openMidiFileProperty().addListener((obs, oldVal, newVal) -> {
            destroyPianos();

            for (int channel : newVal.getChannels()) {
                PianoView piano = new PianoView(channel);
                pianos.add(piano);
                this.player.addMidiEventListener(piano);
            }
        });
        this.model.loopingProperty().addListener((obs, oldVal, newVal) -> {
            player.setLooping(newVal);
        });

        ColorTextureLoader colorLoader = StandardTextureLoaders.RGBA_COLOR_LOADER;
        TextureData white = colorLoader.load(ColorTextureSpec.create("fefde7", 1, 1));
        TextureData black = colorLoader.load(ColorTextureSpec.create("333", 1, 1));
        TextureData playingWhite = colorLoader.load(ColorTextureSpec.create("f00", 1, 1));
        TextureData playingBlack = colorLoader.load(ColorTextureSpec.create("990", 1, 1));
        TextureCollection textures = TextureCollection.of(ImmutableBiMap.of(
                white, "white",
                black, "black",
                playingWhite, "playing-white",
                playingBlack, "playing-black"));
        TextureAtlas atlas = TextureAtlas.create(512, 512, textures);

        this.atlas = ctx.getTextureProvider().load(atlas.getData(),
                TextureSettings.builder()
                        .downscaling(Downscaling.NEAREST)
                        .upscaling(Upscaling.NEAREST)
                        .textureWrapping(TextureWrap.CLAMP_TO_EDGE)
                        .build());

        // 30x30x150 white keys
        this.whiteNote = ctx.getShapes().rectPrism().shape(Vector3d.ZERO,
                new Vector3d(WHITE_NOTE_WIDTH, WHITE_NOTE_LENGTH, WHITE_NOTE_HEIGHT),
                new CubeLayout(atlas, textures).all("white").build())
                .asBindable().get();
        this.whiteNote.initialize();
        this.playingWhiteNote = ctx.getShapes().rectPrism().shape(Vector3d.ZERO,
                new Vector3d(WHITE_NOTE_WIDTH, WHITE_NOTE_LENGTH, WHITE_NOTE_HEIGHT),
                new CubeLayout(atlas, textures).all("playing-white").build())
                .asBindable().get();
        this.playingWhiteNote.initialize();
        // 20x20x50 black keys
        this.blackNote = ctx.getShapes().rectPrism().shape(Vector3d.ZERO,
                new Vector3d(BLACK_NOTE_WIDTH, BLACK_NOTE_LENGTH, BLACK_NOTE_HEIGHT),
                new CubeLayout(atlas, textures).all("black").build())
                .asBindable().get();
        this.blackNote.initialize();
        this.playingBlackNote = ctx.getShapes().rectPrism().shape(Vector3d.ZERO,
                new Vector3d(BLACK_NOTE_WIDTH, BLACK_NOTE_LENGTH, BLACK_NOTE_HEIGHT),
                new CubeLayout(atlas, textures).all("playing-black").build())
                .asBindable().get();
        this.playingBlackNote.initialize();

        updateViewMatrix();
    }

    @Subscribe
    public void keyEvent(KeyStateEvent event) {
        if (event.is(Key.W, KeyState.REPEATED) || event.is(Key.W, KeyState.PRESSED)) {
            eulerAngles = eulerAngles.add(1, 0, 0);
            updateViewMatrix();
        }
        if (event.is(Key.S, KeyState.REPEATED) || event.is(Key.S, KeyState.PRESSED)) {
            eulerAngles = eulerAngles.sub(1, 0, 0);
            updateViewMatrix();
        }
        if (event.is(Key.R, KeyState.PRESSED)) {
            Vector2i size = window.getSize();
            setProjection(size.getX(), size.getY());
        }
    }

    private void updateViewMatrix() {
        view = Matrix4f.createTranslation(0, -1500, -3500)
                .rotate(Quaternionf.fromAxesAnglesDeg(-eulerAngles.getX(), -eulerAngles.getY(), -eulerAngles.getZ()));
    }

    @Override
    public void destroy() {
        destroyPianos();
        this.whiteNote.destroy();
        this.blackNote.destroy();
        this.playingWhiteNote.destroy();
        this.playingBlackNote.destroy();
    }

    private void destroyPianos() {
        this.pianos.forEach(player::removeMidiEventListener);
        this.pianos.clear();
    }

    @Override
    public void draw() {
        try (Bindable texture = this.atlas.bind()) {
            try (BindableDrawable whiteBound = whiteNote.bind()) {
                drawNotes(whiteBound, true, false);
            }
            try (BindableDrawable whiteBound = playingWhiteNote.bind()) {
                drawNotes(whiteBound, true, true);
            }

            try (BindableDrawable blackBound = blackNote.bind()) {
                drawNotes(blackBound, false, false);
            }
            try (BindableDrawable blackBound = playingBlackNote.bind()) {
                drawNotes(blackBound, false, true);
            }
        }
    }

    private Vector3f baseVector(int piano) {
        return new Vector3f(-PIANO_WIDTH / 2f, piano * PIANO_DY, piano * PIANO_DZ);
    }

    private void drawNotes(BindableDrawable note, boolean drawingWhite, boolean drawDown) {
        for (int i = 0; i < this.pianos.size(); i++) {
            PianoView view = this.pianos.get(i);

            Vector3f base = baseVector(i);
            for (int key = 0; key < PIANO_SIZE; key++) {
                if (drawingWhite != isWhiteKey(key)) {
                    continue;
                }
                int vel = view.getVelocity(key);
                boolean keyDown = vel != 0;
                if (keyDown != drawDown) {
                    continue;
                }
                Vector3f trans = base.add(OFFSETS[key]);
                if (vel > 0) {
                    // move down based on pressure
                    float pressureMove = (vel / 128f) * 10f;
                    trans = trans.add(0, -pressureMove, 0);
                }
                mvp(trans, Quaternionf.IDENTITY);
                note.drawWithoutBinding();
            }
        }
    }

    private void mvp(Vector3f translation, Quaternionf rotation) {
        ctx.getMatrixUploader().upload(Matrix4f.createRotation(rotation).translate(translation), view, projection);
    }

    @Subscribe
    public void onResize(WindowResizeEvent event) {
        setProjection(event.getSize().getX(), event.getSize().getY());
    }

    private void setProjection(int width, int height) {
        this.projection = Matrix4f.createPerspective(40, width / height, 100, -100);
    }

}
