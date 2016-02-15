package com.datdo.mobilib.carrier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.datdo.mobilib.event.MblCommonEvents;
import com.datdo.mobilib.event.MblEventCenter;
import com.datdo.mobilib.event.MblEventListener;
import com.datdo.mobilib.util.MblUtils;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * <pre>
 * Carrier/Interceptor is a alternative of old Activity/Fragment model.
 *
 * Due to the fact that Activity/Fragment model has too many drawbacks:
 *   1. Quite complicated to start and manage lifecycle.
 *      :::How you start a Fragment with parameters
 *      {@code
 *      Fragment newFragment = new ExampleFragment();
 *      Bundle args = new Bundle();
 *      args.putInt("param1", param1);
 *      args.putInt("param2", param2);
 *      newFragment.setArguments(args);
 *      FragmentTransaction transaction = getFragmentManager().beginTransaction(); // or getSupportFragmentManager()
 *      transaction.replace(R.id.fragment_container, newFragment);
 *      transaction.addToBackStack(null);
 *      transaction.commit();
 *      }
 *      :::Fragment 's lifecycle (quite different from Activity 's lifecycle, why Google didn't make coding simpler?)
 *      onAttach -> onCreate -> onCreateView -> onActivityCreated -> onStart -> onResume -> onPause -> onStop -> onDestroyView -> onDestroy -> onDetach

 *   2. Cause potential bugs (especially {@code Fragment#getActivity()} method which causes {@link java.lang.NullPointerException}.
 *   3. Fragment can not contain another fragment (for example: you can not add Google MapFragment into your fragment)
 *   4. Unable to start a fragment directly from another fragment while an Activity can be started directly from another Activity (you can do it by using getActivity() method, but it is still complicated, as mentioned in [1])
 *   5. Activity must be subclass of FragmentActivity.
 *
 * it is recommended to use Carrier/Interceptor alternative when you need to render multiple sub-screens in a parent screen.
 *
 * Benefits of Carrier/Interceptor:
 *   1. Easy to use
 *     :::How you start an Interceptor with parameters
 *     {@code
 *     carrier.startInterceptor(ExampleInterceptor.class, null, "param1", param1, "param2", param2);
 *     }
 *     :::Interceptor 's lifecycle just looks like Activity 's lifecycle, even simpler
 *     onCreate -> onResume -> onPause -> onDestroy
 *   2. Interceptor can contains another interceptor due to the fact that interceptor is just a View
 *   3. You can start an interceptor from another interceptor, just like starting Activity from another Activity, even simpler
 *     {@code
 *     public class ExampleInterceptor extends MblInterceptor {
 *         public void foo() {
 *             startInterceptor(NextInterceptor.class, null, "param1", param1, "param2", param2);
 *         }
 *     }
 *     }
 *   4. MblCarrier is just an object wrapping a {@link FrameLayout} view which is the container view of its Interceptors, therefore Carrier can be plugged in any Activity or View.
 *
 * Sample code:
 *
 * {@code
 *      FrameLayout interceptorContainerView = (FrameLayout) findViewById(R.id.interceptor_container);
 *      mCarrier = new MblSlidingCarrier(this, interceptorContainerView, new MblCarrier.MblCarrierCallback() {
 *          @Override
 *          public void onNoInterceptor() {
 *              // ... handle when Carrier does not contain any Interceptor
 *          }
 *      });
 *      mCarrier.startInterceptor(Interceptor1.class, new Options().newInterceptorStack(), "param1", param1);
 * }
 *
 * P/S: the name "Carrier/Interceptor" is inspired by legendary game Starcraft ;)
 * </pre>
 * @see com.datdo.mobilib.carrier.MblInterceptor
 */
@SuppressLint("InflateParams")
public abstract class MblCarrier implements MblEventListener {

    private static final String TAG = MblUtils.getTag(MblCarrier.class);

    /**
     * <pre>
     * Extra options when starting new interceptor.
     * All options is OFF (false) by default.
     * </pre>
     */
    public static final class Options {
        private boolean mNewInterceptorStack;
        private boolean mNoAnimation;

        /**
         * <pre>
         * Clear all interceptor in stack before starting new interceptor.
         * </pre>
         * @return this
         */
        public Options newInterceptorStack() {
            mNewInterceptorStack = true;
            return this;
        }

        /**
         * Configure whether animation is played when interceptor is started/finished.
         */
        public Options noAnimation() {
            mNoAnimation = true;
            return this;
        }
    }

    /**
     * Callback interface for {@link com.datdo.mobilib.carrier.MblCarrier}.
     */
    public static class MblCarrierCallback {
        public void beforeStart(Class<? extends MblInterceptor> clazz, Options options, Map<String, Object> extras) {}
        public void afterStart(Class<? extends MblInterceptor> clazz, Options options, Map<String, Object> extras) {}
        public void beforeFinish(MblInterceptor currentInterceptor) {}
        public void afterFinish(MblInterceptor currentInterceptor) {}
    }

    protected Context                   mContext;
    protected FrameLayout               mInterceptorContainerView;
    protected MblCarrierCallback        mCallback;
    private boolean                     mInterceptorBeingStarted;
    private final Stack<MblInterceptor> mInterceptorStack = new Stack<MblInterceptor>();


    /**
     * Constructor
     * @param context activity in which this Carrier is plugged
     * @param interceptorContainerView view that contains all Interceptors of this Carrier
     * @param callback instance of {@link com.datdo.mobilib.carrier.MblCarrier.MblCarrierCallback}
     */
    public MblCarrier(Context context, FrameLayout interceptorContainerView, MblCarrierCallback callback) {

        mContext                    = context;
        mInterceptorContainerView   = interceptorContainerView;
        mCallback                   = callback != null ? callback : new MblCarrierCallback();

        MblEventCenter.addListener(this, new String[] {
                MblCommonEvents.ACTIVITY_RESUMED,
                MblCommonEvents.ACTIVITY_PAUSED,
                MblCommonEvents.ACTIVITY_DESTROYED
        });
    }

    /**
     * <pre>
     * Overridden by subclasses.
     * This method defines the animations when navigating from currentInterceptor to nextInterceptor.
     * </pre>
     * @param currentInterceptor interceptor which is currently displayed
     * @param nextInterceptor next interceptor to navigate
     * @param onAnimationEnd invoked when animation has ended
     */
    protected abstract void animateForStarting(
            final MblInterceptor    currentInterceptor,
            final MblInterceptor    nextInterceptor,
            final Runnable          onAnimationEnd);

    /**
     * <pre>
     * Overridden by subclasses.
     * This method defines the animations when navigating from current interceptor back to previous interceptor.
     * </pre>
     * @param currentInterceptor interceptor which is currently displayed
     * @param previousInterceptor previous interceptor to navigate back
     * @param onAnimationEnd invoked when animation has ended
     */
    protected abstract void animateForFinishing(
            final MblInterceptor currentInterceptor,
            final MblInterceptor previousInterceptor,
            final Runnable onAnimationEnd);

    /**
     * Destroy all interceptors.
     */
    public void finishAllInterceptors() {
        if (isVisible()) {
            onPause();
        }
        destroyAllInterceptors();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEvent(Object sender, String name, Object... args) {
        Activity activity = (Activity) args[0];
        if (activity != mContext) {
            return;
        }

        if (MblCommonEvents.ACTIVITY_RESUMED == name) {
            if (isVisible()) {
                onResume();
            }
        }

        if (MblCommonEvents.ACTIVITY_PAUSED == name) {
            if (isVisible()) {
                onPause();
            }
        }

        if (MblCommonEvents.ACTIVITY_DESTROYED == name) {
            onDestroy();
        }
    }

    /**
     * Start an interceptor for this Carrier.
     * @param clazz class of interceptor to start
     * @param options extra option when adding new interceptor to carrier
     * @param extras parameters passed to the new interceptor, in key,value (for example: "param1", param1Value, "param1", param2Value, ...)
     * @return the new interceptor instance
     */
    public MblInterceptor startInterceptor(Class<? extends MblInterceptor> clazz, Options options, Object... extras) {
        return startInterceptor(clazz, options, convertExtraArrayToMap(extras));
    }

    /**
     * Start an interceptor for this Carrier.
     * @param clazz class of interceptor to start
     * @param options extra option when adding new interceptor to carrier
     * @param extras parameters passed to the new interceptor, in key,value
     * @return the new interceptor instance
     */
    public MblInterceptor startInterceptor(final Class<? extends MblInterceptor> clazz, final Options options, final Map<String, Object> extras) {

        if (mInterceptorBeingStarted) {
            return null;
        }

        mInterceptorBeingStarted = true;

        if (options != null) {
            if (options.mNewInterceptorStack) {
                finishAllInterceptors();
            }
        }

        try {
            final MblInterceptor nextInterceptor = clazz.getConstructor(Context.class, MblCarrier.class, Map.class).newInstance(mContext, this, extras);

            if (mInterceptorStack.isEmpty()) {
                mCallback.beforeStart(clazz, options, extras);
                mInterceptorContainerView.addView(nextInterceptor);
                mInterceptorStack.push(nextInterceptor);
                nextInterceptor.onResume();
                mInterceptorBeingStarted = false;
                mCallback.afterStart(clazz, options, extras);
            } else {
                mCallback.beforeStart(clazz, options, extras);
                final MblInterceptor currentInterceptor = mInterceptorStack.peek();
                mInterceptorContainerView.addView(nextInterceptor);
                mInterceptorStack.push(nextInterceptor);

                Runnable action = new Runnable() {
                    @Override
                    public void run() {
                        currentInterceptor.setVisibility(View.INVISIBLE);
                        currentInterceptor.onPause();
                        nextInterceptor.onResume();
                        mInterceptorBeingStarted = false;
                        mCallback.afterStart(clazz, options, extras);
                    }
                };

                if (options != null && options.mNoAnimation) {
                    action.run();
                } else {
                    animateForStarting(currentInterceptor, nextInterceptor, action);
                }
            }
            return nextInterceptor;
        } catch (Throwable e) {
            Log.e(TAG, "Unable to start interceptor: " + clazz, e);
            return null;
        }
    }

    /**
     * Destroy an interceptor.
     * @param currentInterceptor interceptor to destroy
     */
    public void finishInterceptor(MblInterceptor currentInterceptor) {
        finishInterceptor(currentInterceptor, null);
    }

    /**
     * Destroy an interceptor with options
     * @param currentInterceptor interceptor to destroy
     * @param options extra option when finishing an interceptor
     */
    public void finishInterceptor(final MblInterceptor currentInterceptor, Options options) {

        try {
            boolean isTop = currentInterceptor == mInterceptorStack.peek();
            if (isTop) {
                mCallback.beforeFinish(currentInterceptor);
                mInterceptorStack.pop();
                if (mInterceptorStack.isEmpty()) {
                    // just remove top interceptor
                    mInterceptorContainerView.removeView(currentInterceptor);
                    currentInterceptor.onPause();
                    currentInterceptor.onDestroy();
                    mCallback.afterFinish(currentInterceptor);
                } else {
                    final MblInterceptor previousInterceptor = mInterceptorStack.peek();
                    previousInterceptor.setVisibility(View.VISIBLE);

                    Runnable action = new Runnable() {
                        @Override
                        public void run() {
                            mInterceptorContainerView.removeView(currentInterceptor);
                            currentInterceptor.onPause();
                            currentInterceptor.onDestroy();
                            previousInterceptor.onResume();
                            mCallback.afterFinish(currentInterceptor);
                        }
                    };

                    if (options != null && options.mNoAnimation) {
                        action.run();
                    } else {
                        animateForFinishing(currentInterceptor, previousInterceptor, action);
                    }
                }
            } else {
                // just remove interceptor from stack silently
                mCallback.beforeFinish(currentInterceptor);
                mInterceptorStack.remove(currentInterceptor);
                mInterceptorContainerView.removeView(currentInterceptor);
                currentInterceptor.onPause();
                currentInterceptor.onDestroy();
                mCallback.afterFinish(currentInterceptor);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Unable to finish interceptor: " + currentInterceptor, e);
        }
    }

    /**
     * <pre>
     * Bring an interceptor to front.
     * Do nothing if interceptor is not in this carrier, or it is already on top of stack.
     * </pre>
     */
    public void bringToFront(MblInterceptor interceptor) {
        if (interceptor == null
                || !mInterceptorStack.contains(interceptor)
                || mInterceptorContainerView.indexOfChild(interceptor) < 0) {
            return;
        }

        boolean isTop = interceptor == mInterceptorStack.peek();
        if (isTop) {
            return;
        }

        MblInterceptor topInterceptor = mInterceptorStack.peek();

        mInterceptorStack.remove(interceptor);
        mInterceptorStack.push(interceptor);

        topInterceptor.setVisibility(View.INVISIBLE);
        topInterceptor.onPause();
        interceptor.setVisibility(View.VISIBLE);
        interceptor.onResume();
    }

    /**
     * Manually trigger onResume() for top interceptor.
     */
    public void onResume() {
        try {
            MblInterceptor currentInterceptor = mInterceptorStack.peek();
            currentInterceptor.onResume();
        } catch (Throwable e) {
            Log.e(TAG, "Unable to handle onResume()", e);
        }
    }

    /**
     * Manually trigger onPause() for top interceptor.
     */
    public void onPause() {
        try {
            MblInterceptor currentInterceptor = mInterceptorStack.peek();
            currentInterceptor.onPause();
        } catch (Throwable e) {
            Log.e(TAG, "Unable to handle onPause()", e);
        }
    }

    /**
     * Manually trigger onDestroy() for all interceptors.
     */
    public void onDestroy() {
        destroyAllInterceptors();
    }

    private void destroyAllInterceptors() {
        try {
            while(!mInterceptorStack.isEmpty()) {
                MblInterceptor interceptor = mInterceptorStack.pop();
                interceptor.onDestroy();
                mInterceptorContainerView.removeView(interceptor);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Unable to destroy all interceptors", e);
        }
    }

    /**
     * <pre>
     * Handle when user pressed Android Back button.
     * This method is called by parent Activity of this carrier
     * {@code
     *      public class ExampleActivity extends MblBaseActivity {
     *          @Override
     *          public void onBackPressed() {
     *              if (mCarrier.onBackPressed()) {
     *                  return;
     *              }
     *              // ...
     *              super.onBackPressed();
     *          }
     *      }
     * }
     * </pre>
     * @return true if this event is handled by current interceptor
     */
    public boolean onBackPressed() {
        try {
            MblInterceptor currentInterceptor = mInterceptorStack.peek();
            return currentInterceptor.onBackPressed();
        } catch (Throwable e) {
            Log.e(TAG, "Unable to handle onBackPressed()", e);
            return false;
        }
    }

    /**
     * <pre>
     * Handle when parent Activity of this carrier receives activity result.
     * This method is called by parent Activity of this carrier
     * {@code
     *      public class ExampleActivity extends MblBaseActivity {
     *          @Override
     *          public void onActivityResult(int requestCode, int resultCode, Intent data) {
     *              if (mCarrier.onActivityResult(requestCode, resultCode, data)) {
     *                  return;
     *              }
     *              // ...
     *              super.onActivityResult();
     *          }
     *      }
     * }
     * </pre>
     * @return true if this event is handled by current interceptor
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            MblInterceptor currentInterceptor = mInterceptorStack.peek();
            return currentInterceptor.onActivityResult(requestCode, resultCode, data);
        } catch (Throwable e) {
            Log.e(TAG, "Unable to handle onBackPressed()", e);
            return false;
        }
    }

    static Map<String, Object> convertExtraArrayToMap(Object... extras) {

        Assert.assertTrue(extras == null || extras.length % 2 == 0);

        Map<String, Object> mapExtras = new HashMap<String, Object>();
        if (!MblUtils.isEmpty(extras)) {
            int i = 0;
            while (i < extras.length) {
                Object key = extras[i];
                Object value = extras[i+1];
                Assert.assertTrue(key != null && key instanceof String);
                if (value != null) {
                    mapExtras.put((String) key, value);
                }
                i += 2;
            }
        }

        return mapExtras;
    }

    /**
     * Get {@link com.datdo.mobilib.carrier.MblInterceptor} instances bound with this carrier
     * @return
     */
    public List<MblInterceptor> getInterceptors() {
        return new ArrayList<MblInterceptor>(mInterceptorStack);
    }

    /**
     * Show/hide this carrier and all of its children.
     */
    public void setVisible(boolean isVisible) {
        if (isVisible() != isVisible) {
            if (isVisible) {
                mInterceptorContainerView.setVisibility(View.VISIBLE);
                onResume();
            } else {
                mInterceptorContainerView.setVisibility(View.INVISIBLE);
                onPause();
            }
        }
    }

    /**
     * Check if this carrier is visible.
     */
    public boolean isVisible() {
        return mInterceptorContainerView.getVisibility() == View.VISIBLE;
    }
}
