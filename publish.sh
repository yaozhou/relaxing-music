#!/bin/sh

ant debug
adb uninstall com.ayao.player
adb install bin/MyPlayer-debug.apk