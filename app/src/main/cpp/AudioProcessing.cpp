#include <jni.h>
#include <string>
#include <android/log.h>
#include <AndroidIO/SuperpoweredAndroidAudioIO.h>
#include <SuperpoweredAdvancedAudioPlayer.h>
#include <SuperpoweredSimple.h>
#include <SuperpoweredCPU.h>
#include <malloc.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>
#include <SLES/OpenSLES.h>
#include <SuperpoweredTimeStretching.h>
#include <SuperpoweredNBandEQ.h>
#include <SuperpoweredRecorder.h>
#include <SuperpoweredMixer.h>

#define log_print __android_log_print

//region variables' definitions

static SuperpoweredAndroidAudioIO *audioIO;
static SuperpoweredAdvancedAudioPlayer *player;
static SuperpoweredNBandEQ *eq;
static SuperpoweredRecorder *recorder;
static SuperpoweredStereoMixer *mixer;
static float *playbackBuffer, *recordBuffer, *outputBuffer, tempoRate;
static float eqFreqList[] = {64.0f, 150.0f, 350.0f, 1000.0f, 2000.0f, 6000.0f, 12000.0f, 0.0f};
static const char *dest;
static const char *temp;
static int _sampleRate, _bufferSize, shiftCents;
static bool isRecording, isVocalRemoved;
static float* buffers[] = {NULL, NULL, NULL, NULL};

static float inputLevels[] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
static float outputLevels[] = {1.0f, 1.0f};
static double moveDistanceMs = 5000;

//endregion

// This is called periodically by the audio engine.
static bool audioProcessing (
        void * __unused clientdata, // custom pointer
        short int *audio,           // buffer of interleaved samples
        int numberOfFrames,         // number of frames to process
        int __unused samplerate     // sampling rate
) {
    player->setPitchShiftCents(shiftCents);
    player->setTempo(tempoRate, true);
    bool result = player->process(playbackBuffer, false, (unsigned int)numberOfFrames , 1.0f);
    if(result) {
        eq->process(playbackBuffer, playbackBuffer, (unsigned int) numberOfFrames);

        if(isVocalRemoved) {
            SuperpoweredStereoToMono(playbackBuffer, outputBuffer, 1.0f, 1.0f, -1.0f, -1.0f, (unsigned int) numberOfFrames);
            SuperpoweredInterleave(outputBuffer, outputBuffer, playbackBuffer,(unsigned int) numberOfFrames );
        }
        if (isRecording) {
            SuperpoweredShortIntToFloat(audio, recordBuffer, (unsigned int) numberOfFrames);

            float *buffers[] = {playbackBuffer, recordBuffer, NULL, NULL};
            float *outputs[] = {playbackBuffer, NULL};

            mixer->process(buffers, outputs, inputLevels, outputLevels, NULL, NULL,
                           (unsigned int) numberOfFrames);
            recorder->process(playbackBuffer, (unsigned int) numberOfFrames);
        }
        SuperpoweredFloatToShortInt(playbackBuffer, audio, (unsigned int)numberOfFrames);
    }
    return result;
}

// Called by the player.
static void playerEventCallback (
        void * __unused clientData,
        SuperpoweredAdvancedAudioPlayerEvent event,
        void *value
) {
    switch (event) {
        case SuperpoweredAdvancedAudioPlayerEvent_LoadSuccess:
            break;
        case SuperpoweredAdvancedAudioPlayerEvent_LoadError:
            log_print(ANDROID_LOG_ERROR, "PlayerExample", "Open error: %s", (char *)value);
            break;
        case SuperpoweredAdvancedAudioPlayerEvent_EOF:
            player->seek(0);    // loop track
            break;
        default:;
    };
}

static

// This is called after the recorder closed the WAV file.
static void recorderStopped (void * __unused clientdata) {
    delete recorder;
}

static void createAudioIO (bool inputEnabled, bool resetAudioIO = false){
    if (audioIO != NULL && resetAudioIO)
        delete audioIO ;
    int latency = inputEnabled ? 10 : 0;
    audioIO = new SuperpoweredAndroidAudioIO (
            _sampleRate,                    // sampling rate
            _bufferSize,                    // buffer size
            inputEnabled,                   // enableInput
            true,                           // enableOutput
            audioProcessing,                // process callback function
            NULL,                            // clientData
            -1,
            -1,
            latency
    );
}

//region exported methods

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_SetPitchAndRate (
        JNIEnv * __unused env,
        jobject  __unused obj,
        jfloat p_shift,
        jfloat p_rate
) {
    tempoRate = 1 + p_rate*0.05f;
    shiftCents = static_cast<int>(p_shift*100);
}

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_EQActivity_SetEQBand (
        JNIEnv * __unused env,
        jobject  __unused obj,
        jint p_bandIndex,
        jint p_gain
) {
    eq->setBand((unsigned int)p_bandIndex, p_gain);
}

// StartAudio - Start audio engine and initialize player.
extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_StartAudio (
        JNIEnv * __unused env,
        jobject  __unused obj,
        jint samplerate,
        jint buffersize,
        jstring tempPath,       // path to a temporary file
        jstring destPath        // path to the destination file
) {
    // Allocate audio buffer.
    playbackBuffer = (float *)malloc(sizeof(float) * 2 * buffersize);
    recordBuffer = (float *)malloc(sizeof(float) * 2 * buffersize);
    outputBuffer = (float *)malloc(sizeof(float) * 2 * buffersize);

    tempoRate =0.0f;
    shiftCents=0;

    //initialize n-band EQ
    float* ptrEqFreqList = eqFreqList;
    eq = new SuperpoweredNBandEQ((unsigned int)samplerate, ptrEqFreqList);
    eq->enable(true);

    // Get path strings.
    temp = env->GetStringUTFChars(tempPath, 0);
    dest = env->GetStringUTFChars(destPath, 0);

    _sampleRate = samplerate;
    _bufferSize = buffersize;
    isRecording = false;

    mixer = new SuperpoweredStereoMixer();

    // Initialize player and pass callback function.
    player = new SuperpoweredAdvancedAudioPlayer (
            NULL,                           // clientData
            playerEventCallback,            // callback function
            (unsigned int)_sampleRate,   // sampling rate
            0                               // cachedPointCount
    );

    createAudioIO(false);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_Simoorg_EasyTrackManipulator_MainActivity_GetPosition(
        JNIEnv * __unused env,
        jobject __unused obj
)
{
    return player->positionMs;
}

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_SetPosition(
        JNIEnv * __unused env,
        jobject __unused obj,
        jdouble position
)
{
    return player->setPosition(position, false, false);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_Simoorg_EasyTrackManipulator_MainActivity_GetDuration(
        JNIEnv * __unused env,
        jobject __unused obj
)
{
    return player->durationMs;
}

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_ToggleRemoveVocal(
        JNIEnv * __unused env,
        jobject __unused obj
)
{
    isVocalRemoved = !isVocalRemoved;
}

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_MoveForward(
        JNIEnv * __unused env,
        jobject __unused obj
)
{
    double position = player->positionMs;
    if(position + moveDistanceMs < (double)player->durationMs){
        player->setPosition(position + moveDistanceMs, false, false);
    } else{
        player->setPosition((double)player->durationMs, false, false);
    }
}

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_MoveBackward(
        JNIEnv * __unused env,
        jobject __unused obj
)
{
    double position = player->positionMs;
    if(position - moveDistanceMs > 0){
        player->setPosition(position - moveDistanceMs, false, false);
    } else{
        player->setPosition(0, false, false);
    }
}

extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_ToggleRecording(
        JNIEnv * __unused env,
        jobject __unused obj
)
{
    if(!isRecording) {
        createAudioIO(true, true);
        // Initialize the recorder with a temporary file path.
        recorder = new SuperpoweredRecorder(
                temp,               // The full filesystem path of a temporarily file.
                (unsigned int) _sampleRate,   // Sampling rate.
                1,                  // The minimum length of a recording (in seconds).
                2,                  // The number of channels.
                false,              // applyFade (fade in/out at the beginning / end of the recording)
                recorderStopped,    // Called when the recorder finishes writing after stop().
                NULL                // A custom pointer your callback receives (clientData).
        );
        isRecording = true;
        recorder->start(dest);
    } else
    {
        isRecording = false;
        recorder->stop();
        createAudioIO(false, true);
    }
}

// OpenFile - Open file in player, specifying offset and length.
extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_OpenFileFromPath (
        JNIEnv *env,
        jobject __unused obj,
        jstring path
) {
    if(player != NULL)
        delete player;
    // Initialize player and pass callback function.
    player = new SuperpoweredAdvancedAudioPlayer (
            NULL,                           // clientData
            playerEventCallback,            // callback function
            (unsigned int)_sampleRate,   // sampling rate
            0                               // cachedPointCount
    );
    const char *str = env->GetStringUTFChars(path, 0);
    player->open(str);
    env->ReleaseStringUTFChars(path, str);
}

// TogglePlayback - Toggle Play/Pause state of the player.
extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_TogglePlayback (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    player->togglePlayback();
    SuperpoweredCPU::setSustainedPerformanceMode(player->playing);  // prevent dropouts
}

// onBackground - Put audio processing to sleep.
extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_onBackground (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    audioIO->onBackground();
}

// onForeground - Resume audio processing.
extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_onForeground (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    audioIO->onForeground();
}

// Cleanup - Free resources.
extern "C" JNIEXPORT void
Java_com_Simoorg_EasyTrackManipulator_MainActivity_Cleanup (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    delete audioIO;
    delete player;
    delete eq;
    delete mixer;
    if(recorder!= NULL)
        delete recorder;
    free(playbackBuffer);
    free(recordBuffer);
    free(outputBuffer);
}

//endregion
