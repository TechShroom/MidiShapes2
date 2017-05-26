package com.techshroom.midishapes.midi.player;

import com.techshroom.midishapes.midi.event.channel.NoteOffEvent;
import com.techshroom.midishapes.midi.event.channel.NoteOnEvent;

class MidiSoundManager implements MidiSoundPlayer {

    static MidiSoundManager manangeFont(MidiSoundfont sounds) {
        return new MidiSoundManager();
    }

    private MidiSoundManager() {
    }
    
    void noteOn(NoteOnEvent event) {
        
    }
    
    void noteOff(NoteOffEvent event) {
        
    }

}
