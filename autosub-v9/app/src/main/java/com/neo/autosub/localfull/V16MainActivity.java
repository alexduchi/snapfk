package com.neo.autosub.localfull;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.VideoView;

public class V16MainActivity extends V14MainActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().post(() -> {
            VideoView video = findVideoView(getWindow().getDecorView());
            if (video == null) return;
            video.setOnPreparedListener(mp -> fitVideoInsidePreview(video, mp));
        });
    }

    private VideoView findVideoView(View view) {
        if (view instanceof VideoView) return (VideoView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                VideoView found = findVideoView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void fitVideoInsidePreview(VideoView video, MediaPlayer player) {
        if (!(video.getParent() instanceof FrameLayout)) return;
        FrameLayout parent = (FrameLayout) video.getParent();
        parent.post(() -> {
            int frameW = parent.getWidth();
            int frameH = parent.getHeight();
            int videoW = player.getVideoWidth();
            int videoH = player.getVideoHeight();
            if (frameW <= 0 || frameH <= 0 || videoW <= 0 || videoH <= 0) return;

            float videoAspect = videoW / (float) videoH;
            float frameAspect = frameW / (float) frameH;
            int drawW;
            int drawH;
            if (videoAspect > frameAspect) {
                drawW = frameW;
                drawH = Math.max(1, Math.round(frameW / videoAspect));
            } else {
                drawH = frameH;
                drawW = Math.max(1, Math.round(frameH * videoAspect));
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(drawW, drawH, Gravity.CENTER);
            video.setLayoutParams(lp);
        });
    }
}
