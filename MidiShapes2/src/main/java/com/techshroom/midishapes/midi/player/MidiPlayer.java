package com.techshroom.midishapes.midi.player;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.AbstractIterator;
import com.techshroom.midishapes.midi.MidiFile;
import com.techshroom.midishapes.midi.event.MidiEvent;

/**
 * Asynchronous MIDI file player.
 */
public class MidiPlayer {

    private final MidiEngine engine = new MidiEngine();
    private volatile MidiFile midiFile;
    private MidiSoundfont sounds = MidiSoundfont.getDefault();

    public void setSounds(MidiSoundfont sounds) {
        this.sounds = sounds;
    }

    public MidiSoundfont getSounds() {
        return sounds;
    }

    public void play(MidiFile file) {
        stop();

        midiFile = file;
        engine.start(file.getTimingData(), sounds, reduceTracks(file));
    }

    /**
     * Turns the separate tracks into a single iterator sorted by tick.
     */
    private Iterator<MidiEvent> reduceTracks(MidiFile file) {
        return new AbstractIterator<MidiEvent>() {

            private int[] indexes = new int[file.getTracks().size()];

            @Override
            protected MidiEvent computeNext() {
                MidiEvent next = null;
                for (int i = 0; i < file.getTracks().size(); i++) {
                    MidiEvent atList = listGet(i);
                    if (atList == null) {
                        continue;
                    }

                    if (next == null) {
                        next = atList;
                    } else {
                        if (next.getTick() > atList.getTick()) {
                            next = atList;
                        }
                    }
                }
                return next == null ? endOfData() : next;
            }

            @Nullable
            private MidiEvent listGet(int index) {
                int listIndex = indexes[index];
                List<MidiEvent> list = file.getTracks().get(index).getEvents();
                if (listIndex >= list.size()) {
                    return null;
                }
                return list.get(listIndex);
            }
        };
    }

    public boolean isRunning() {
        return midiFile != null;
    }

    public void stop() {
        engine.stop();
        midiFile = null;
    }

}
