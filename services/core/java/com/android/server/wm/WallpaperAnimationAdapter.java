/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.wm;

import static com.android.server.wm.AnimationAdapterProto.REMOTE;
import static com.android.server.wm.RemoteAnimationAdapterWrapperProto.TARGET;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_RECENTS_ANIMATIONS;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_REMOTE_ANIMATIONS;

import android.graphics.Point;
import android.os.SystemClock;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * An animation adapter for wallpaper windows.
 */
class WallpaperAnimationAdapter implements AnimationAdapter {
    private static final String TAG = "WallpaperAnimationAdapter";

    private final WallpaperWindowToken mWallpaperToken;
    private SurfaceControl mCapturedLeash;
    private SurfaceAnimator.OnAnimationFinishedCallback mCapturedLeashFinishCallback;

    private long mDurationHint;
    private long mStatusBarTransitionDelay;

    private Consumer<WallpaperAnimationAdapter> mAnimationCanceledRunnable;
    private RemoteAnimationTarget mTarget;

    WallpaperAnimationAdapter(WallpaperWindowToken wallpaperToken,
            long durationHint, long statusBarTransitionDelay,
            Consumer<WallpaperAnimationAdapter> animationCanceledRunnable) {
        mWallpaperToken = wallpaperToken;
        mDurationHint = durationHint;
        mStatusBarTransitionDelay = statusBarTransitionDelay;
        mAnimationCanceledRunnable = animationCanceledRunnable;
    }

    /**
     * Creates and starts remote animations for all the visible wallpaper windows.
     *
     * @return RemoteAnimationTarget[] targets for all the visible wallpaper windows
     */
    public static RemoteAnimationTarget[] startWallpaperAnimations(WindowManagerService service,
            long durationHint, long statusBarTransitionDelay,
            Consumer<WallpaperAnimationAdapter> animationCanceledRunnable,
            ArrayList<WallpaperAnimationAdapter> adaptersOut) {
        final ArrayList<RemoteAnimationTarget> targets = new ArrayList<>();
        service.mRoot.forAllWallpaperWindows(wallpaperWindow -> {
            if (!wallpaperWindow.getDisplayContent().mWallpaperController.isWallpaperVisible()) {
                if (DEBUG_REMOTE_ANIMATIONS || DEBUG_RECENTS_ANIMATIONS) {
                    Slog.d(TAG, "\tNot visible=" + wallpaperWindow);
                }
                return;
            }

            if (DEBUG_REMOTE_ANIMATIONS || DEBUG_RECENTS_ANIMATIONS) {
                Slog.d(TAG, "\tvisible=" + wallpaperWindow);
            }
            final WallpaperAnimationAdapter wallpaperAdapter = new WallpaperAnimationAdapter(
                    wallpaperWindow, durationHint, statusBarTransitionDelay,
                    animationCanceledRunnable);
            wallpaperWindow.startAnimation(wallpaperWindow.getPendingTransaction(),
                    wallpaperAdapter, false /* hidden */);
            targets.add(wallpaperAdapter.createRemoteAnimationTarget());
            adaptersOut.add(wallpaperAdapter);
        });
        return targets.toArray(new RemoteAnimationTarget[targets.size()]);
    }

    /**
     * Create a remote animation target for this animation adapter.
     */
    RemoteAnimationTarget createRemoteAnimationTarget() {
        mTarget = new RemoteAnimationTarget(-1, -1, getLeash(), false, null, null,
                mWallpaperToken.getPrefixOrderIndex(), new Point(), null,
                mWallpaperToken.getWindowConfiguration(), true, null, null);
        return mTarget;
    }

    /**
     * @return the leash for this animation (only valid after the wallpaper window surface animation
     * has started).
     */
    SurfaceControl getLeash() {
        return mCapturedLeash;
    }

    /**
     * @return the callback to call to clean up when the animation has finished.
     */
    SurfaceAnimator.OnAnimationFinishedCallback getLeashFinishedCallback() {
        return mCapturedLeashFinishCallback;
    }

    /**
     * @return the wallpaper window
     */
    WallpaperWindowToken getToken() {
        return mWallpaperToken;
    }

    @Override
    public boolean getShowWallpaper() {
        // Not used
        return false;
    }

    @Override
    public void startAnimation(SurfaceControl animationLeash, SurfaceControl.Transaction t,
            SurfaceAnimator.OnAnimationFinishedCallback finishCallback) {
        if (DEBUG_REMOTE_ANIMATIONS) Slog.d(TAG, "startAnimation");

        // Restore z-layering until client has a chance to modify it.
        t.setLayer(animationLeash, mWallpaperToken.getPrefixOrderIndex());
        mCapturedLeash = animationLeash;
        mCapturedLeashFinishCallback = finishCallback;
    }

    @Override
    public void onAnimationCancelled(SurfaceControl animationLeash) {
        if (DEBUG_REMOTE_ANIMATIONS) Slog.d(TAG, "onAnimationCancelled");
        mAnimationCanceledRunnable.accept(this);
    }

    @Override
    public long getDurationHint() {
        return mDurationHint;
    }

    @Override
    public long getStatusBarTransitionsStartTime() {
        return SystemClock.uptimeMillis() + mStatusBarTransitionDelay;
    }

    @Override
    public void dump(PrintWriter pw, String prefix) {
        pw.print(prefix);
        pw.print("token=");
        pw.println(mWallpaperToken);
        if (mTarget != null) {
            pw.print(prefix);
            pw.println("Target:");
            mTarget.dump(pw, prefix + "  ");
        } else {
            pw.print(prefix);
            pw.println("Target: null");
        }
    }

    @Override
    public void writeToProto(ProtoOutputStream proto) {
        final long token = proto.start(REMOTE);
        if (mTarget != null) {
            mTarget.writeToProto(proto, TARGET);
        }
        proto.end(token);
    }
}