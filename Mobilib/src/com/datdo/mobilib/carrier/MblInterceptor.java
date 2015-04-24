package com.datdo.mobilib.carrier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.datdo.mobilib.carrier.MblCarrier.Events;
import com.datdo.mobilib.event.MblEventCenter;
import com.datdo.mobilib.util.MblUtils;

/**
 * <pre>
 * Interceptor class of Carrier/Interceptor model.
 * Interceptor is designed to be as identical to Activity as possible, so that coding life is easier.
 * Note that subclass of this class must have a public constructor like this
 * {@code
 *      public class Interceptor1 extends MblInterceptor {
 *          public Interceptor1(Context context, Map<String, Object> extras) {
 *              super(context, extras);
 *              // ... do something here to initialize this interceptor
 *          }
 *      }
 * }
 * </pre>
 * @see com.datdo.mobilib.carrier.MblCarrier
 */
@SuppressLint("InflateParams")
public abstract class MblInterceptor extends FrameLayout {

    private boolean                     mIsTop;
    private final Map<String, Object>   mExtras = new ConcurrentHashMap<String, Object>();

    protected MblInterceptor(Context context, Map<String, Object> extras) {
        super(context);
        mExtras.clear();
        if (!MblUtils.isEmpty(extras)) {
            mExtras.putAll(extras);
        }
        onCreate();
    }

    /**
     * Get parameter passed to this interceptor.
     * @param key parameter name
     * @param defaultVal default value if param is not found
     * @return value of parameter
     */
    public Object getExtra(String key, Object defaultVal) {
        Object ret = mExtras.get(key);
        if (ret != null) {
            return ret;
        } else {
            return defaultVal;
        }
    }

    /**
     * Set content view for this interceptor
     * @see android.app.Activity#setContentView(int)
     */
    public void setContentView(int layoutResId) {
        View contentView = (ViewGroup) MblUtils.getLayoutInflater().inflate(layoutResId, null);
        setContentView(contentView);
    }

    /**
     * Set content view for this interceptor
     * @see android.app.Activity#setContentView(View)
     */
    public void setContentView(View contentView) {
        removeAllViews();
        addView(contentView);
    }

    /**
     * Create a View from its layout ID.
     */
    public View inflate(int layoutResId) {
        return MblUtils.getLayoutInflater().inflate(layoutResId, null);
    }

    /**
     * Finish this interceptor.
     * @see android.app.Activity#finish()
     */
    public void finish() {
        MblEventCenter.postEvent(this, Events.FINISH_INTERCEPTOR);
    }

    /**
     * Start another interceptor.
     * @param clazz class of interceptor to start
     * @param extras parameters passed to the new interceptor, in key,value (for example: "param1", param1Value, "param1", param2Value, ...)
     */
    public void startInterceptor(Class<? extends MblInterceptor> clazz, Object... extras) {
        startInterceptor(clazz, MblCarrier.convertExtraArrayToMap(extras));
    }

    /**
     * Start another interceptor.
     * @param clazz class of interceptor to start
     * @param extras parameters passed to the new interceptor, in key,value
     */
    public void startInterceptor(Class<? extends MblInterceptor> clazz, Map<String, Object> extras) {
        MblEventCenter.postEvent(this, Events.START_INTERCEPTOR, clazz, extras);
    }

    /**
     * Invoked when this interceptor is created.
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    public void onCreate() {}

    /**
     * Invoked when this interceptor is displayed.
     * @see android.app.Activity#onResume()
     */
    public void onResume() {
        mIsTop = true;
    }

    /**
     * Invoked when this interceptor is not displayed any more (destroyed or navigate to other interceptor)
     * @see android.app.Activity#onPause()
     */
    public void onPause() {
        mIsTop = false;
    }

    /**
     * Invoked when this interceptor is detached from parent carrier and ready to be recycled by Garbage Collector.
     * @see android.app.Activity#onDestroy()
     */
    public void onDestroy() {
        MblUtils.cleanupView(this);
    }

    /**
     * Invoked when parent Activity received activity result.
     * @return true if this interceptor handled the activity result
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }

    /**
     * Invoked when user presses Android Back button
     * @return true if this interceptor handled the event
     * @see android.app.Activity#onBackPressed()
     */
    public boolean onBackPressed() {
        finish();
        return true;
    }

    /**
     * Check if this interceptor is on top of interceptor stack of its parent carrier.
     */
    public boolean isTop() {
        return mIsTop;
    }
}