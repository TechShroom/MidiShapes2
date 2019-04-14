rootProject.name = "MidiShapes2"
val includer = file(".includer.gradle")
if (includer.exists()) {
    apply(from = includer)
}
