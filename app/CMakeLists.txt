cmake_minimum_required(VERSION 3.4.1)

# convert SDK path to forward slashes on Windows
file(TO_CMAKE_PATH ${PATH_TO_SUPERPOWERED} PATH_TO_SUPERPOWERED)

include_directories(src/main/cpp)
include_directories(${PATH_TO_SUPERPOWERED})

# compile player example to a native library
add_library (
        AudioProcessing
        SHARED
        src/main/cpp/AudioProcessing.cpp
        ${PATH_TO_SUPERPOWERED}/AndroidIO/SuperpoweredAndroidAudioIO.cpp
        ${PATH_TO_SUPERPOWERED}/SuperpoweredNBandEQ.cpp
)

# link the native library against the following libraries
target_link_libraries (
        AudioProcessing
        log
        android
        OpenSLES
        ${PATH_TO_SUPERPOWERED}/libSuperpoweredAndroid${ANDROID_ABI}.a
)
