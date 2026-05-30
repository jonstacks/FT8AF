# FT8AF native USB audio module.
#
# Phase 1: this builds a stub libft8af_usb.so alongside the existing
# pre-built libft8cn.so (packaged via jniLibs.srcDirs = ['libs']). The
# stub returns a build-string from native so we can verify the NDK +
# CMake + JNI plumbing is working end-to-end before we vendor libusb
# proper in the next phase.
#
# Naming: distinct from libft8cn.so to avoid collision with the existing
# prebuilt FT8 decoder library.

cmake_minimum_required(VERSION 3.18.1)

project(ft8af_native)

add_library(
        ft8af_usb
        SHARED
        usb_audio.cpp
)

find_library(log-lib log)

target_link_libraries(
        ft8af_usb
        ${log-lib}
)
