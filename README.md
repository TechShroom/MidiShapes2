UnplannedDescent
================

The third iteration of TechShroom Libraries. A rebuild; like LWJGL 3.

Build
-----
[![Build Status](https://travis-ci.org/TechShroom/UnplannedDescent.svg?branch=master)](https://travis-ci.org/TechShroom/UnplannedDescent)

Setup
-----
This repo is a workspace from Eclipse, without the Eclipse files. UDAPI, UDCore, UDGraphics, UDKeyboard, UDMouse, UDUtil, and UDWindows are each eclipse projects, but can be used with anything else.

Gradle is used to build everything. Normally `gradle build` should cover everything, placing each in `build/libs` under each project.

Note for eclipse: There's a gradle bug where the classpath can be duplicated on two runs of `gradle eclipse`, so there's an included `reeclipse` task that fixes the problem. Use it if you need to refresh.
