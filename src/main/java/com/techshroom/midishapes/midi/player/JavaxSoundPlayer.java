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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.techshroom.midishapes.midi.event.MidiEvent;
import com.techshroom.midishapes.midi.event.channel.ChannelEvent;
import com.techshroom.midishapes.midi.event.encode.MidiEventEncoder;
import com.techshroom.unplanned.window.dialog.GLOptionPane;

final class JavaxSoundPlayer implements MidiSoundPlayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaxSoundPlayer.class);

    private static final Function<MidiDevice.Info, String> DEVICE_INFO_TO_STRING = i -> {
        return String.format("%s %s: %s", i.getName(), i.getVersion(), i.getDescription());
    };
    private static final JavaxSoundPlayer INSTANCE = new JavaxSoundPlayer();

    static JavaxSoundPlayer getInstance() {
        return INSTANCE;
    }

    private final Lock lock = new ReentrantLock();
    private final List<MidiDevice.Info> deviceInfos;
    private Soundbank activeSoundbank;
    private MidiDevice.Info deviceInfo;
    private MidiEventEncoder enc;
    private MidiDevice device;
    private Receiver target;
    private boolean open;

    private JavaxSoundPlayer() {
        LOGGER.info("Loading MIDI info...");
        List<MidiDevice.Info> infos = new ArrayList<>(Arrays.asList(MidiSystem.getMidiDeviceInfo()));
        boolean hasVirMidi = false;
        for (Iterator<MidiDevice.Info> iterator = infos.iterator(); iterator.hasNext();) {
            MidiDevice.Info info = iterator.next();
            MidiDevice dev;
            if (!hasVirMidi) {
                try {
                    dev = MidiSystem.getMidiDevice(info);
                } catch (MidiUnavailableException e) {
                    e.printStackTrace();
                    continue;
                }
                if (dev.getMaxReceivers() == 0) {
                    LOGGER.info("Skipping " + info.getName() + ": no receivers");
                    iterator.remove();
                    continue;
                }
                if (info.getName().contains("VirMIDI")) {
                    hasVirMidi = true;
                }
            } else if (info.getName().contains("VirMIDI")) {
                iterator.remove();
                continue;
            }
            LOGGER.debug(info.getName());
        }
        deviceInfos = ImmutableList.copyOf(infos);
        checkState(deviceInfos.size() > 0, "there are no MIDI devices on this system");
        setDeviceInfo(deviceInfos.get(0));
    }

    private void setDeviceInfo(MidiDevice.Info info) {
        lock.lock();
        try {
            if (device != null) {
                device.close();
                device = null;
            }
            deviceInfo = info;
            try {
                if (open) {
                    close();
                }

                device = MidiSystem.getMidiDevice(info);

                if (open) {
                    open();
                }

                if (device instanceof Synthesizer) {
                    Synthesizer s = (Synthesizer) device;
                    if (activeSoundbank != null) {
                        if (!s.isOpen()) {
                            s.open();
                        }
                        // we have pre-loaded a soundbank on another device
                        s.unloadAllInstruments(s.getDefaultSoundbank());
                        if (!s.loadAllInstruments(activeSoundbank)) {
                            LOGGER.warn("Failed to load old soundbank on new device");
                            // fall back to default
                            activeSoundbank = null;
                        }
                    }
                    if (activeSoundbank == null) {
                        activeSoundbank = s.getDefaultSoundbank();
                        s.unloadAllInstruments(activeSoundbank);
                        s.loadAllInstruments(activeSoundbank);
                    }
                }
            } catch (MidiUnavailableException e) {
                throw new RuntimeException(e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void openSettingsPanel() {
        MidiDevice.Info selected = GLOptionPane.showInputDialog("Choose a MIDI Device", "Choose a MIDI Device",
                deviceInfos, deviceInfo, DEVICE_INFO_TO_STRING);
        if (selected != null) {
            setDeviceInfo(selected);
        }
    }

    @Override
    public void setSoundfont(Path sf2File) {
        Soundbank sb;
        try {
            sb = MidiSystem.getSoundbank(sf2File.toFile());
        } catch (InvalidMidiDataException | IOException e) {
            throw new IllegalStateException("failed to load SF2", e);
        }
        if (!loadSoundbank(sb)) {
            LOGGER.warn("Ignoring soundbank " + sb.getName() + ", as it could not be loaded or used.");
        }
    }

    private boolean loadSoundbank(Soundbank sb) {
        lock.lock();
        try {
            if (device instanceof Synthesizer) {
                Synthesizer s = (Synthesizer) device;
                // open the device
                if (!device.isOpen()) {
                    try {
                        device.open();
                    } catch (MidiUnavailableException e) {
                        throw new IllegalStateException("Failed to open device", e);
                    }
                }
                if (s.isSoundbankSupported(sb)) {
                    if (activeSoundbank != null) {
                        if (activeSoundbank.equals(sb)) {
                            return true;
                        }
                        s.unloadAllInstruments(activeSoundbank);
                    }
                    if (s.loadAllInstruments(sb)) {
                        activeSoundbank = sb;
                        LOGGER.info("Loaded {} as soundbank!", sb.getName());
                        return true;
                    }
                    // fall back to old if not loaded
                    s.loadAllInstruments(activeSoundbank);
                }
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    @Override
    public JavaxSoundPlayer open() {
        lock.lock();
        try {
            device.open();
            target = device.getReceiver();
            checkNotNull(target, "no receiver opened!");
            enc = MidiEventEncoder.getInstance();
            if (activeSoundbank != null) {
                loadSoundbank(activeSoundbank);
            }
        } catch (MidiUnavailableException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
        return this;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            enc = null;
            if (target != null) {
                target.close();
                target = null;
            }
            setDeviceInfo(deviceInfo);
        } finally {
            lock.unlock();
        }
    }

    private static final class SimpleMessage extends MidiMessage {

        protected SimpleMessage(byte[] data) {
            super(data);
        }

        @Override
        public Object clone() {
            return new SimpleMessage(data);
        }

    }

    @Override
    public void onEvent(MidiEventChain chain) {
        MidiEvent e = chain.currentEvent();
        if (e instanceof ChannelEvent) {
            handleEveryEvent((ChannelEvent) e);
        }
        chain.sendCurrentEventToNext();
    }

    private void handleEveryEvent(ChannelEvent event) {
        lock.lock();
        try {
            if (target == null) {
                return;
            }
            // TODO handle closing of async chain
            // checkState(target != null, "not opened");
            target.send(new SimpleMessage(enc.encode(event)), -1);
        } finally {
            lock.unlock();
        }
    }

}
