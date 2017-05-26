package com.techshroom.midishapes.midi.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.techshroom.midishapes.midi.event.MidiEvent;

class MidiState {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiState.class);

    private final EventBus events = new EventBus("midi-state");

    MidiState(MidiSoundfont sounds) {
        addHandler(this);
        addHandler(MidiSoundManager.manangeFont(sounds));
    }

    void addHandler(Object object) {
        events.register(object);
    }

    void onEvent(MidiEvent event) {
        events.post(event);
    }

    @Subscribe
    public void deadEvent(DeadEvent event) {
        LOGGER.warn("Unhandled event " + event.getEvent());
    }
}
