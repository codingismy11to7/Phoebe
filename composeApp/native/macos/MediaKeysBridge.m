#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <MediaPlayer/MediaPlayer.h>
#import <jni.h>

static JavaVM *g_vm = NULL;
static jclass g_macSessionClass = NULL;
static jmethodID g_midDispatchToggle = NULL;
static jmethodID g_midDispatchPlay = NULL;
static jmethodID g_midDispatchPause = NULL;
static jmethodID g_midDispatchNext = NULL;
static jmethodID g_midDispatchPrev = NULL;
static jmethodID g_midDispatchSeek = NULL;

static NSString *g_nowPlayingTitle = nil;
static NSString *g_nowPlayingArtist = nil;
static NSString *g_nowPlayingAlbum = nil;
static NSString *g_nowPlayingArtworkUrl = nil;
static MPMediaItemArtwork *g_nowPlayingArtwork = nil;
static double g_nowPlayingPosition = 0.0;
static double g_nowPlayingDuration = 0.0;
static BOOL g_nowPlayingIsPlaying = NO;
static BOOL g_artworkLoadAttempted = NO;
static NSInteger g_artworkGeneration = 0;

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

static void attach_and_call_long(jmethodID mid, jlong value) {
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
    (*env)->CallStaticVoidMethod(env, g_macSessionClass, mid, value);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (attachedHere == JNI_TRUE) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
}

static NSString *jstring_to_nsstring(JNIEnv *env, jstring value) {
    if (value == NULL) {
        return @"";
    }
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (chars == NULL) {
        return @"";
    }
    NSString *string = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, value, chars);
    return string != nil ? string : @"";
}

static NSURL *artwork_url(NSString *raw) {
    NSString *trimmed = [raw stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (trimmed.length == 0 || [trimmed hasPrefix:@"web-storage://"]) {
        return nil;
    }
    if ([trimmed hasPrefix:@"file://"] || [trimmed hasPrefix:@"http://"] || [trimmed hasPrefix:@"https://"]) {
        return [NSURL URLWithString:trimmed];
    }
    if ([trimmed hasPrefix:@"/"]) {
        return [NSURL fileURLWithPath:trimmed];
    }
    return nil;
}

static MPMediaItemArtwork *load_artwork(NSString *rawUrl) {
    NSURL *url = artwork_url(rawUrl);
    if (url == nil) {
        return nil;
    }
    NSData *data = [NSData dataWithContentsOfURL:url];
    if (data.length == 0) {
        return nil;
    }
    NSImage *image = [[NSImage alloc] initWithData:data];
    if (image == nil) {
        return nil;
    }
    return [[MPMediaItemArtwork alloc] initWithBoundsSize:CGSizeMake(600.0, 600.0)
                                           requestHandler:^NSImage *_Nonnull(CGSize size) {
        (void)size;
        return image;
    }];
}

static void publish_current_now_playing(void) {
    if (g_nowPlayingTitle.length == 0 && g_nowPlayingDuration <= 0.0) {
        [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:nil];
        [MPNowPlayingInfoCenter defaultCenter].playbackState = MPNowPlayingPlaybackStateStopped;
        return;
    }

    NSMutableDictionary *info = [NSMutableDictionary dictionary];
    info[MPMediaItemPropertyTitle] = g_nowPlayingTitle.length > 0 ? g_nowPlayingTitle : @"Phoebe";
    info[MPMediaItemPropertyArtist] = g_nowPlayingArtist.length > 0 ? g_nowPlayingArtist : @"";
    info[MPMediaItemPropertyAlbumTitle] = g_nowPlayingAlbum.length > 0 ? g_nowPlayingAlbum : @"";
    info[MPNowPlayingInfoPropertyMediaType] = @(MPNowPlayingInfoMediaTypeAudio);
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(g_nowPlayingPosition);
    info[MPNowPlayingInfoPropertyPlaybackRate] = @(g_nowPlayingIsPlaying ? 1.0 : 0.0);
    info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = @(1.0);
    if (g_nowPlayingDuration > 0.0) {
        info[MPMediaItemPropertyPlaybackDuration] = @(g_nowPlayingDuration);
    }
    if (g_nowPlayingArtwork != nil) {
        info[MPMediaItemPropertyArtwork] = g_nowPlayingArtwork;
    }

    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:info];
    [MPNowPlayingInfoCenter defaultCenter].playbackState =
        g_nowPlayingIsPlaying ? MPNowPlayingPlaybackStatePlaying : MPNowPlayingPlaybackStatePaused;
}

static void schedule_artwork_load_if_needed(void) {
    if (g_nowPlayingArtworkUrl.length == 0 || g_nowPlayingArtwork != nil || g_artworkLoadAttempted) {
        return;
    }
    NSString *targetUrl = [g_nowPlayingArtworkUrl copy];
    NSInteger generation = g_artworkGeneration;
    g_artworkLoadAttempted = YES;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
        MPMediaItemArtwork *artwork = load_artwork(targetUrl);
        dispatch_async(dispatch_get_main_queue(), ^{
            if (generation != g_artworkGeneration || ![targetUrl isEqualToString:g_nowPlayingArtworkUrl]) {
                return;
            }
            g_nowPlayingArtwork = artwork;
            publish_current_now_playing();
        });
    });
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

    MPRemoteCommand *seek = c.changePlaybackPositionCommand;
    seek.enabled = YES;
    [seek removeTarget:nil];
    [seek addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *_Nonnull event) {
        MPChangePlaybackPositionCommandEvent *seekEvent = (MPChangePlaybackPositionCommandEvent *)event;
        jlong positionMs = (jlong)(seekEvent.positionTime * 1000.0);
        attach_and_call_long(g_midDispatchSeek, positionMs);
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
    [c.changePlaybackPositionCommand removeTarget:nil];
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:nil];
    [MPNowPlayingInfoCenter defaultCenter].playbackState = MPNowPlayingPlaybackStateStopped;
    g_nowPlayingTitle = nil;
    g_nowPlayingArtist = nil;
    g_nowPlayingAlbum = nil;
    g_nowPlayingArtworkUrl = nil;
    g_nowPlayingArtwork = nil;
    g_artworkLoadAttempted = NO;
    g_artworkGeneration++;
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
    g_midDispatchSeek = (*env)->GetStaticMethodID(env, clazz, "dispatchSeekFromNative", "(J)V");

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
    g_midDispatchSeek = NULL;
    g_vm = NULL;
}

JNIEXPORT void JNICALL
Java_com_phoebe_app_media_MacMediaSession_nativeUpdateNowPlaying(JNIEnv *env,
                                                                 jclass clazz,
                                                                 jstring jtitle,
                                                                 jstring jartist,
                                                                 jstring jalbum,
                                                                 jstring jartworkUrl,
                                                                 jlong positionMs,
                                                                 jlong durationMs,
                                                                 jboolean playing) {
    (void)clazz;
    NSString *title = jstring_to_nsstring(env, jtitle);
    NSString *artist = jstring_to_nsstring(env, jartist);
    NSString *album = jstring_to_nsstring(env, jalbum);
    NSString *artworkUrl = jstring_to_nsstring(env, jartworkUrl);
    double pos = (double)positionMs / 1000.0;
    double dur = (double)durationMs / 1000.0;
    BOOL isPlaying = playing == JNI_TRUE;

    dispatch_async(dispatch_get_main_queue(), ^{
        g_nowPlayingTitle = title != nil ? title : @"";
        g_nowPlayingArtist = artist != nil ? artist : @"";
        g_nowPlayingAlbum = album != nil ? album : @"";
        g_nowPlayingPosition = pos;
        g_nowPlayingDuration = dur;
        g_nowPlayingIsPlaying = isPlaying;

        NSString *trimmedArtworkUrl = @"";
        if (artworkUrl != nil) {
            trimmedArtworkUrl = [artworkUrl stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
        }
        NSString *currentArtworkUrl = g_nowPlayingArtworkUrl != nil ? g_nowPlayingArtworkUrl : @"";
        if (![trimmedArtworkUrl isEqualToString:currentArtworkUrl]) {
            g_nowPlayingArtworkUrl = trimmedArtworkUrl;
            g_nowPlayingArtwork = nil;
            g_artworkLoadAttempted = NO;
            g_artworkGeneration++;
        }

        publish_current_now_playing();
        schedule_artwork_load_if_needed();
    });
}
