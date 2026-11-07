package com.zeekoltd.setat.pro;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import eightbitlab.com.blurview.BlurTarget;

/**
 * Bridges existing view hierarchies to BlurView 3.x's BlurTarget API.
 * It wraps the target as a single child so the target's own layout and
 * its descendants' layout params remain intact.
 */
public final class BlurTargetHelper {
    private BlurTargetHelper() {}

    public static BlurTarget ensure(ViewGroup requestedTarget) {
        if (requestedTarget == null) return null;

        ViewGroup target = requestedTarget;
        // The decor content container itself is owned by Android. Wrap its
        // activity content child instead of trying to replace the decor view.
        if (target.getId() == android.R.id.content && target.getChildCount() > 0) {
            View child = target.getChildAt(0);
            if (child instanceof BlurTarget) return (BlurTarget) child;
            if (child instanceof ViewGroup) target = (ViewGroup) child;
            else return wrapLeaf(target, child);
        }

        if (target instanceof BlurTarget) return (BlurTarget) target;
        if (target.getParent() instanceof BlurTarget) return (BlurTarget) target.getParent();

        if (!(target.getParent() instanceof ViewGroup)) {
            // Detached targets cannot be replaced in their parent yet. Create
            // a target wrapper and retain the content; callers should normally
            // pass an attached view hierarchy.
            BlurTarget wrapper = new BlurTarget(target.getContext());
            wrapper.addView(target, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return wrapper;
        }

        ViewGroup parent = (ViewGroup) target.getParent();
        int index = parent.indexOfChild(target);
        ViewGroup.LayoutParams originalParams = target.getLayoutParams();
        parent.removeView(target);

        BlurTarget wrapper = new BlurTarget(target.getContext());
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        wrapper.setLayoutParams(originalParams);
        parent.addView(wrapper, Math.min(index, parent.getChildCount()));

        target.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        wrapper.addView(target);
        return wrapper;
    }

    private static BlurTarget wrapLeaf(ViewGroup parent, View child) {
        int index = parent.indexOfChild(child);
        ViewGroup.LayoutParams originalParams = child.getLayoutParams();
        parent.removeView(child);
        BlurTarget wrapper = new BlurTarget(child.getContext());
        wrapper.setLayoutParams(originalParams);
        parent.addView(wrapper, Math.min(index, parent.getChildCount()));
        wrapper.addView(child, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return wrapper;
    }
}
