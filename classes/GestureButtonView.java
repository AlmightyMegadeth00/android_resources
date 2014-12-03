/*
 * Copyright (C) 2014 VanirAOSP
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

package com.android.systemui.statusbar.policy;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.widget.ImageView;

import com.android.internal.util.vanir.VanirActions;
import com.android.internal.util.vanir.NavbarConstants.NavbarConstant;
import com.android.internal.util.vanir.NavbarUtils;
import com.android.systemui.R;

/* This class is an extension of KeyButtonView the touch handling class extending the view for the soft keys in Android.  It's 
 * a demo of a gesture listener I've written that accepts short left/right, long left/right, press, double press, long press,
 * and upwards swipe with sloppy touch handling on each side.  It can take two KeyButtonInfo classes populated from string-packed 
 * settings constants for a completely customizable gesture-based experience.  GestureButtonView can either replace a single button, multiple
 * buttons, or stretch the entire length of the navigation bar. 
*/

public class GestureButtonView extends KeyButtonView {
    private static final String TAG = "StatusBar.GuestureButtonView";
    private static final boolean DEBUG = true;

    public static final int SLOPPY_LONGPRESS_TIMEOUT = 500;

    int mTouchSlop;
    boolean mIsRight;
    boolean mVertical;

    private GestureDetector mGestureDetector;
    View.OnTouchListener mGestureListener;
    Handler mHandler = new Handler();

    KeyButtonInfo mLeftActions;
    KeyButtonInfo mRightActions;

    private boolean mHasSingleAction = true,
            mHasDoubleAction, mHasLongAction, mHasSwipeLeftAction,
            mHasSwipeRightAction, mHasSwipeRightShortAction, mHasSwipeLeftShortAction, mHasSwipeUpAction;
    private boolean mHasRightSingleAction = true,
            mHasRightDoubleAction, mHasRightLongAction, mHasRightSwipeLeftAction,
            mHasRightSwipeRightAction, mHasRightSwipeRightShortAction, mHasRightSwipeLeftShortAction, mHasRightSwipeUpAction;

    Runnable mSingleTapTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mIsRight) {
                VanirActions.launchAction(mContext, mLeftActions.singleAction);
            } else {
                VanirActions.launchAction(mContext, mRightActions.singleAction);
            }
        }
    };

    public GestureButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        mButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_gesture_key_width);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setLongClickable(false);

        mGestureDetector = new GestureDetector(context, new KeyGestureDetector());
        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                mGestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
    }
 
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector != null) mGestureDetector.onTouchEvent(event);
        return true;
    }
 
    @Override public void setImage() { }
 
    @Override public void setImage(final Resources res) { }
 
    @Override public void setButtonActions(KeyButtonInfo actions) { }

    public void setLeftActions(KeyButtonInfo actions) {
        this.mLeftActions = actions;
        if (mLeftActions != null) {
            mHasSingleAction = mLeftActions.singleAction != null;
            mHasLongAction = mLeftActions.longPressAction != null;
            mHasDoubleAction = mLeftActions.doubleTapAction != null;
            mHasSwipeLeftAction = mLeftActions.swipeLeft != null;
            mHasSwipeRightAction = mLeftActions.swipeRight != null;
            mHasSwipeLeftShortAction = mLeftActions.swipeLeftShort != null;
            mHasSwipeRightShortAction = mLeftActions.swipeRightShort != null;
            mHasSwipeUpAction = mLeftActions.swipeUp != null;
        }
    }

    public void setRightActions(KeyButtonInfo actions) {
        this.mRightActions = actions;
        if (mRightActions != null) {
            mHasRightSingleAction = mRightActions.singleAction != null;
            mHasRightLongAction = mRightActions.longPressAction != null;
            mHasRightDoubleAction = mRightActions.doubleTapAction != null;
            mHasRightSwipeLeftAction = mRightActions.swipeLeft != null;
            mHasRightSwipeRightAction = mRightActions.swipeRight != null;
            mHasRightSwipeLeftShortAction = mRightActions.swipeLeftShort != null;
            mHasRightSwipeRightShortAction = mRightActions.swipeRightShort != null;
            mHasRightSwipeUpAction = mRightActions.swipeUp != null;
        }
    }

    public void setIsVertical(boolean isVertical) {
        mVertical = isVertical;
    }

    class KeyGestureDetector extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_LONG_DISTANCE = 130;
        private static final int SWIPE_MIN_DISTANCE = 50;
        private static final int SWIPE_MAX_OFF_PATH = 200;
        private static final int SWIPE_THRESHOLD_VELOCITY = 250;
        private final int SINGLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout() - 150;

        @Override
        public boolean onDown(MotionEvent e) {
            Log.e(TAG, "onDown");
            float length = mVertical ? getHeight() : getWidth();
            float pos = mVertical ? e.getY() : e.getX();
            length /= 2;
            mIsRight = mVertical ? pos < length : pos > length;

            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            mHandler.removeCallbacks(mSingleTapTimeout);
            return false;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.e(TAG, "onSingleTapUp");
            if (!mIsRight) {
                if (mHasSingleAction && !mHasDoubleAction) {
                    VanirActions.launchAction(mContext, mLeftActions.singleAction);
                    return true;
                }
            } else {
                if (mHasRightSingleAction && !mHasRightDoubleAction) {
                    VanirActions.launchAction(mContext, mRightActions.singleAction);
                    return true;
                }
            }
            if (mHasSingleAction || mHasRightSingleAction) {
                mHandler.postDelayed(mSingleTapTimeout, SINGLE_TAP_TIMEOUT);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.e(TAG, "onDoubleTap");
            mHandler.removeCallbacks(mSingleTapTimeout);
            if (!mIsRight) {
                if (mHasDoubleAction) {
                    VanirActions.launchAction(mContext, mLeftActions.doubleTapAction);
                }
            } else {
                if (mHasRightDoubleAction) {
                    VanirActions.launchAction(mContext, mRightActions.doubleTapAction);
                }
            }
			return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.e(TAG, "onLongPress");
            mHandler.removeCallbacks(mSingleTapTimeout);
            if (!mIsRight) {
                if (mHasLongAction) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    VanirActions.launchAction(mContext, mLeftActions.longPressAction);
                }
            } else {
                if (mHasRightLongAction) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    VanirActions.launchAction(mContext, mRightActions.longPressAction);
                }
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {;
            mHandler.removeCallbacks(mSingleTapTimeout);
            Log.e(TAG, "onFling");

            /* X and Y need to be reversed for phone UI in landscape (mVertical = true) */
            float X1, X2, Y1, Y2;
            if (!mVertical) {
                X1 = e1.getX();
                X2 = e2.getX();
                Y1 = e1.getY();
                Y2 = e2.getY();
            } else {
                X1 = e2.getY();
                X2 = e1.getY();
                Y1 = e1.getX();
                Y2 = e2.getX();
            }

            if (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                if (Math.abs(Y1 - Y2) > SWIPE_MIN_DISTANCE) {
                    // upwards swipe
                    if (!(X1 - X2 > SWIPE_MIN_DISTANCE)
                            && !(X2 - X1 > SWIPE_MIN_DISTANCE)) {
                        if (!mIsRight) {
                            if (mHasSwipeUpAction)
                                    VanirActions.launchAction(mContext, mLeftActions.swipeUp);
                        } else {
                            if (mHasRightSwipeUpAction)
                                    VanirActions.launchAction(mContext, mRightActions.swipeUp);
                        }
                        return true;
                    }
                } else {
                    // left long swipe
                    if (X1 - X2 > SWIPE_LONG_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeLeftAction)
                                    VanirActions.launchAction(mContext, mLeftActions.swipeLeft);
                        } else {
                            if (mHasRightSwipeLeftAction)
                                    VanirActions.launchAction(mContext, mRightActions.swipeLeft);
                        }
                        return false;
                    // right long swipe
                    } else if (X2 - X1 > SWIPE_LONG_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeRightAction)
                                    VanirActions.launchAction(mContext, mLeftActions.swipeRight);
                        } else {
                            if (mHasRightSwipeRightAction)
                                    VanirActions.launchAction(mContext, mRightActions.swipeRight);
                        }
                        return false;
                    }
                }
            
                // left short swipe
                if (X2 - X1 < SWIPE_LONG_DISTANCE) {
                    if (X1 - X2 > SWIPE_MIN_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeLeftShortAction)
                                    VanirActions.launchAction(mContext, mLeftActions.swipeLeftShort);
                        } else {
                            if (mHasRightSwipeLeftShortAction)
                                    VanirActions.launchAction(mContext, mRightActions.swipeLeftShort);
                        }
                        return false;
                    // right short swipe
                    } else if (X2 - X1 > SWIPE_MIN_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeRightShortAction)
                                    VanirActions.launchAction(mContext, mLeftActions.swipeRightShort);
                        } else {
                            if (mHasRightSwipeRightShortAction)
                                    VanirActions.launchAction(mContext, mRightActions.swipeRightShort);
                        }
                        return false;
                    }
                }
            }

            // handle single press events on a sloppy touch
            if ((X1 - X2 < SWIPE_MIN_DISTANCE)
                        || (X2 - X1 < SWIPE_MIN_DISTANCE)) {
                if (!mIsRight) {
                    if (mHasSingleAction) {
                        VanirActions.launchAction(mContext, mLeftActions.singleAction);
                    }
                } else {
                    if (mHasRightSingleAction) {
                        VanirActions.launchAction(mContext, mRightActions.singleAction);
                    }
                }
                return false;
            }
            return false;
        }
    }

	class KeyButtonInfo {
		public static final String NULL_ACTION = AwesomeConstant.ACTION_NULL.value();

		public String singleAction, doubleTapAction, longPressAction, iconUri;
		public String swipeLeft, swipeRight, swipeLeftShort, swipeRightShort, swipeUp;

		// reserved for single purpose keys
		public KeyButtonInfo(String singleTap) {
			this.singleAction = singleTap;
		}

		// reserved for standard configurable buttons
		public KeyButtonInfo(String singleTap, String doubleTap, String longPress, String uri) {
			this.singleAction = singleTap;
			this.doubleTapAction = doubleTap;
			this.longPressAction = longPress;
			this.iconUri = uri;

			if (singleAction != null) {
				if ((singleAction.isEmpty()
						|| singleAction.equals(NULL_ACTION))) {
					singleAction = null;
				}
			}

			if (doubleTapAction != null) {
				if ((doubleTapAction.isEmpty()
						|| doubleTapAction.equals(NULL_ACTION))) {
					doubleTapAction = null;
				}
			}

			if (longPressAction != null) {
				if ((longPressAction.isEmpty()
						|| longPressAction.equals(NULL_ACTION))) {
					longPressAction = null;
				}
			}
		}

		if (iconUri != null) {
			if ((iconUri.isEmpty()
					|| iconUri.equals(NULL_ACTION))) {
				iconUri = null;
			}
		}

		// reserved for gesture-based buttons
		public KeyButtonInfo(String[] actions) {
			if (actions[0] != null) this.singleAction = actions[0];
			if (actions[1] != null) this.doubleTapAction = actions[1];
			if (actions[2] != null) this.longPressAction = actions[2];
			if (actions[3] != null) this.swipeLeft = actions[3];
			if (actions[4] != null) this.swipeRight = actions[4];
			if (actions[5] != null) this.swipeLeftShort = actions[5];
			if (actions[6] != null) this.swipeRightShort = actions[6];
			if (actions[7] != null) this.swipeUp = actions[7];
		}
		
		@Override
		public String toString() {
			return singleAction + "," + doubleTapAction + "," + longPressAction + "," + iconUri;
		}
	}
}
