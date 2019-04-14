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

package com.techshroom.midishapes;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.techshroom.midishapes.MainModule.UnregisteredObjects;
import com.techshroom.midishapes.view.MidiScreenView;
import com.techshroom.unplanned.blitter.GraphicsContext;
import com.techshroom.unplanned.core.util.Sync;
import com.techshroom.unplanned.window.Window;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class MidiShapes {

    public static void main(String[] args) {
        Injector inject = Guice.createInjector(new MainModule());

        ObservableList<Object> unregistered = inject.getInstance(Key.get(new TypeLiteral<ObservableList<Object>>() {},
                UnregisteredObjects.class));

        Window window = inject.getInstance(Window.class);
        GraphicsContext ctx = inject.getInstance(GraphicsContext.class);
        ctx.makeActiveContext();

        MidiScreenModel model = inject.getInstance(MidiScreenModel.class);
        model.initialize();
        MidiScreenView screen = inject.getInstance(MidiScreenView.class);
        screen.initialize();

        unregistered.forEach(window.getEventBus()::register);
        unregistered.clear();
        unregistered.addListener((ListChangeListener<Object>) c -> {
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(window.getEventBus()::register);
                c.getAddedSubList().clear();
            }
        });

        window.setVsyncOn(true);
        window.setVisible(true);

        Sync sync = new Sync();

        while (!window.isCloseRequested()) {
            sync.sync(60);
            window.processEvents();
            ctx.clearGraphicsState();

            model.mainLoop();
            screen.draw();

            ctx.swapBuffers();
        }

        screen.destroy();
        window.destroy();
    }

}
