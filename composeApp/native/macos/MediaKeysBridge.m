#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>
#import <jni.h>
#import <string.h>

static JavaVM *g_vm = NULL;
static jclass g_macSessionClass = NULL;
static jmethodID g_midDispatchToggle = NULL;
static jmethodID g_midDispatchPlay = NULL;
static jmethodID g_midDispatchPause = NULL;
static jmethodID g_midDispatchNext = NULL;
static jmethodID g_midDispatchPrev = NULL;

static void attach_and_call(jmethodID mid) {
    if (g_vm == NULL || g_macSessionClass == NULL || mid == NULL) {
        return;
    }
    JNIEnv *env = NULL;
    jint rc = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
    jint attachedHere = JNI_FALSE;
    if (rc == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != 0) {
            return;
        }
        attachedHere = JNI_TRUE;
    } else if (rc != JNI_OK) {
        return;
    }
    (*env)->CallStaticVoidMethod(env, g_macSessionClass, mid);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (attachedHere == JNI_TRUE) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
}

static void disable_unused_commands(MPRemoteCommandCenter *c) {
    NSArray *cmds = @[
        c.changePlaybackRateCommand,
        c.changeRepeatModeCommand,
        c.changeShuffleModeCommand,
        c.skipForwardCommand,
        c.skipBackwardCommand,
        c.seekForwardCommand,
        c.seekBackwardCommand,
        c.changePlaybackPositionCommand,
        c.ratingCommand,
        c.likeCommand,
        c.dislikeCommand,
        c.bookmarkCommand,
    ];
    for (MPRemoteCommand *cmd in cmds) {
        cmd.enabled = NO;
        [cmd removeTarget:nil];
    }
}

static void register_commands(void) {
    MPRemoteCommandCenter *c = [MPRemoteCommandCenter sharedCommandCenter];
    disable_unused_commands(c);

    MPRemoteCommand *toggle = c.togglePlayPauseCommand;
    toggle.enabled = YES;
    [toggle removeTarget:nil];
    [toggle addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        attach_and_call(g_midDispatchToggle);
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    MPRemoteCommand *play = c.playCommand;
    play.enabled = YES;
    [play removeTarget:nil];
    [play addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        attach_and_call(g_midDispatchPlay);
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    MPRemoteCommand *pause = c.pauseCommand;
    pause.enabled = YES;
    [pause removeTarget:nil];
    [pause addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        attach_and_call(g_midDispatchPause);
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    MPRemoteCommand *next = c.nextTrackCommand;
    next.enabled = YES;
    [next removeTarget:nil];
    [next addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        attach_and_call(g_midDispatchNext);
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    MPRemoteCommand *prev = c.previousTrackCommand;
    prev.enabled = YES;
    [prev removeTarget:nil];
    [prev addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        attach_and_call(g_midDispatchPrev);
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    MPRemoteCommand *stop = c.stopCommand;
    stop.enabled = YES;
    [stop removeTarget:nil];
    [stop addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        attach_and_call(g_midDispatchPause);
        return MPRemoteCommandHandlerStatusSuccess;
    }];
}

static void unregister_commands(void) {
    MPRemoteCommandCenter *c = [MPRemoteCommandCenter sharedCommandCenter];
    [c.togglePlayPauseCommand removeTarget:nil];
    [c.playCommand removeTarget:nil];
    [c.pauseCommand removeTarget:nil];
    [c.nextTrackCommand removeTarget:nil];
    [c.previousTrackCommand removeTarget:nil];
    [c.stopCommand removeTarget:nil];
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:nil];
    [MPNowPlayingInfoCenter defaultCenter].playbackState = MPNowPlayingPlaybackStateStopped;
}

JNIEXPORT void JNICALL Java_com_phoebe_app_media_MacMediaSession_nativeInit(JNIEnv *env, jclass clazz) {
    if ((*env)->GetJavaVM(env, &g_vm) != 0) {
        return;
    }
    g_macSessionClass = (jclass)(*env)->NewGlobalRef(env, clazz);
    g_midDispatchToggle = (*env)->GetStaticMethodID(env, clazz, "dispatchToggleFromNative", "()V");
    g_midDispatchPlay = (*env)->GetStaticMethodID(env, clazz, "dispatchPlayFromNative", "()V");
    g_midDispatchPause = (*env)->GetStaticMethodID(env, clazz, "dispatchPauseFromNative", "()V");
    g_midDispatchNext = (*env)->GetStaticMethodID(env, clazz, "dispatchNextFromNative", "()V");
    g_midDispatchPrev = (*env)->GetStaticMethodID(env, clazz, "dispatchPreviousFromNative", "()V");

    dispatch_async(dispatch_get_main_queue(), ^{
        register_commands();
    });
}

JNIEXPORT void JNICALL Java_com_phoebe_app_media_MacMediaSession_nativeShutdown(JNIEnv *env, jclass clazz) {
    (void)clazz;
    dispatch_sync(dispatch_get_main_queue(), ^{
        unregister_commands();
    });
    if (g_macSessionClass != NULL) {
        (*env)->DeleteGlobalRef(env, g_macSessionClass);
        g_macSessionClass = NULL;
    }
    g_midDispatchToggle = NULL;
    g_midDispatchPlay = NULL;
    g_midDispatchPause = NULL;
    g_midDispatchNext = NULL;
    g_midDispatchPrev = NULL;
    g_vm = NULL;
}

JNIEXPORT void JNICALL
Java_com_phoebe_app_media_MacMediaSession_nativeUpdateNowPlaying(JNIEnv *env,
                                                                 jclass clazz,
                                                                 jstring jtitle,
                                                                 jstring jartist,
                                                                 jlong positionMs,
                                                                 jlong durationMs,
                                                                 jboolean playing) {
    (void)clazz;
    char bufTitle[512] = "";
    char bufArtist[512] = "";
    const char *ct = NULL;
    const char *ca = NULL;
    if (jtitle != NULL) {
        ct = (*env)->GetStringUTFChars(env, jtitle, NULL);
        if (ct != NULL) {
            strncpy(bufTitle, ct, sizeof(bufTitle) - 1);
            (*env)->ReleaseStringUTFChars(env, jtitle, ct);
        }
    }
    if (jartist != NULL) {
        ca = (*env)->GetStringUTFChars(env, jartist, NULL);
        if (ca != NULL) {
            strncpy(bufArtist, ca, sizeof(bufArtist) - 1);
            (*env)->ReleaseStringUTFChars(env, jartist, ca);
        }
    }

    NSString *title = [NSString stringWithUTF8String:bufTitle];
    NSString *artist = [NSString stringWithUTF8String:bufArtist];
    double pos = (double)positionMs / 1000.0;
    double dur = (double)durationMs / 1000.0;
    BOOL isPlaying = playing == JNI_TRUE;

    dispatch_async(dispatch_get_main_queue(), ^{
        if (title.length == 0 && dur <= 0.0) {
            [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:nil];
            [MPNowPlayingInfoCenter defaultCenter].playbackState = MPNowPlayingPlaybackStateStopped;
            return;
        }
        NSMutableDictionary *info = [NSMutableDictionary dictionary];
        info[MPMediaItemPropertyTitle] = title.length > 0 ? title : @"Phoebe";
        info[MPMediaItemPropertyArtist] = artist.length > 0 ? artist : @"";
        info[MPNowPlayingInfoPropertyMediaType] = @(MPNowPlayingInfoMediaTypeAudio);
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(pos);
        info[MPNowPlayingInfoPropertyPlaybackRate] = @(isPlaying ? 1.0 : 0.0);
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = @(1.0);
        if (dur > 0.0) {
            info[MPMediaItemPropertyPlaybackDuration] = @(dur);
        }
        [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:info];
        [MPNowPlayingInfoCenter defaultCenter].playbackState =
            isPlaying ? MPNowPlayingPlaybackStatePlaying : MPNowPlayingPlaybackStatePaused;
    });
}
