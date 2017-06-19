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
package com.techshroom.midishapes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.ProvisionListener;
import com.techshroom.midishapes.midi.player.MidiEventChain;
import com.techshroom.midishapes.midi.player.MidiPlayer;
import com.techshroom.midishapes.midi.player.MidiSoundPlayer;
import com.techshroom.midishapes.view.MidiScreenView;
import com.techshroom.midishapes.view.ViewComponents;
import com.techshroom.unplanned.blitter.GraphicsContext;
import com.techshroom.unplanned.input.Keyboard;
import com.techshroom.unplanned.input.Mouse;
import com.techshroom.unplanned.window.Window;
import com.techshroom.unplanned.window.WindowSettings;

import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MainModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainModule.class);

    @BindingAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
    public @interface UnregisteredObjects {
    }

    private final ObservableList<Object> unregisteredObjects = FXCollections.observableArrayList();

    @Override
    protected void configure() {
        // register everything with event bus
        bindListener(new AbstractMatcher<Binding<?>>() {

            @Override
            public boolean matches(Binding<?> t) {
                return t.getKey().getTypeLiteral().getRawType().getName().startsWith("com.techshroom.midishapes");
            }
        }, new ProvisionListener() {

            @Override
            public <T> void onProvision(ProvisionInvocation<T> provision) {
                LOGGER.info("Registering {} with event bus", provision.getBinding().getKey());
                unregisteredObjects.add(provision.provision());
            }
        });
        // to explicitly register for events
        bind(MidiScreenModel.class).in(Scopes.SINGLETON);
        bind(MidiScreenView.class).in(Scopes.SINGLETON);
        bind(MidiPlayer.class).in(Scopes.SINGLETON);
        bind(ViewComponents.class).in(Scopes.SINGLETON);
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("task-pool-%d")
                .build());
        bind(ExecutorService.class)
                .toInstance(pool);
        bind(ScheduledExecutorService.class)
                .toInstance(pool);
        bind(new TypeLiteral<ObjectProperty<MidiSoundPlayer>>() {})
                .toInstance(new SimpleObjectProperty<>(this, "soundPlayer", MidiSoundPlayer.getDefault()));
        bind(new TypeLiteral<ObservableList<Object>>() {})
                .annotatedWith(UnregisteredObjects.class)
                .toInstance(unregisteredObjects);
    }

    @Provides
    @Singleton
    protected Window createWindow() {
        return WindowSettings.builder()
                .screenSize(1080, 768)
                .title("MIDI Shapes")
                .msaa(true)
                .build().createWindow();
    }

    @Provides
    @Singleton
    protected EventBus primaryEventBus(Window window) {
        return window.getEventBus();
    }

    @Provides
    @Singleton
    protected GraphicsContext provideGraphicsContext(Window window) {
        return window.getGraphicsContext();
    }

    @Provides
    @Singleton
    protected Keyboard provideKeyboard(Window window) {
        return window.getKeyboard();
    }

    @Provides
    @Singleton
    protected Mouse provideMouse(Window window) {
        return window.getMouse();
    }

    @Provides
    @Singleton
    protected ObjectBinding<MidiEventChain> provideChain(MidiPlayer player, ViewComponents view, ObjectProperty<MidiSoundPlayer> soundPlayer) {
        return new ObjectBinding<MidiEventChain>() {

            {
                bind(view.viewEventChainExpression());
                bind(soundPlayer);
            }

            @Override
            protected MidiEventChain computeValue() {
                return player.chainBuilder()
                        .sync()
                        .addAll(view.viewEventChainExpression().get())
                        .add(soundPlayer.get())
                        .build();
            }

        };
    }

}
