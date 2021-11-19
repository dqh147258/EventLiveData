/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.yxf.eventlivedata;

import android.os.Handler;
import android.os.Looper;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.Iterator;
import java.util.Map;

public class EventLiveData<T> extends MutableLiveData<T> {


    public static final int SEND_ONCE = -2;
    public static final int STICKY_FOREVER = -1;
    public static final int NO_STICKY = 0;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Object mDataLock = new Object();
    static final int START_VERSION = -1;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static final Object NOT_SET = new Object();

    private SafeIterableMap<Observer<? super T>, ObserverWrapper> mObservers =
            new SafeIterableMap<>();

    // how many observers are in active state
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            int mActiveCount = 0;
    private volatile Object mData = NOT_SET;
    // when setData is called, we set the pending data and actual data swap happens on the main
    // thread
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    volatile Object mPendingData = NOT_SET;
    private int mVersion = START_VERSION;

    private boolean mDispatchingValue;
    @SuppressWarnings("FieldCanBeLocal")
    private boolean mDispatchInvalidated;
    private final Runnable mPostValueRunnable = new Runnable() {
        @Override
        public void run() {
            Object newValue;
            synchronized (mDataLock) {
                newValue = mPendingData;
                mPendingData = NOT_SET;
            }
            //noinspection unchecked
            setValueInternal((T) newValue);
        }
    };

    private boolean mShouldStickyOnce = false;
    private boolean mInSetValue = false;

    private int mStickyCount = STICKY_FOREVER;
    private int mLeftStickCount = 0;

    private boolean mActiveForever = true;

    public EventLiveData() {

    }

    public EventLiveData(int stickyCount) {
        mStickyCount = stickyCount;
    }

    /**
     * @param stickyCount   粘性事件发送次数,{@link EventLiveData#NO_STICKY}表示不支持粘性事件;
     *                      大于0表示会发送n次粘性事件,当次数用完后不再触发;
     *                      {@link EventLiveData#STICKY_FOREVER} 表示一直支持粘性事件,数据会一直保留
     * @param activeForever 是否永久激活,针对于LifeCycleOwner,
     *                      为true表示除了{@link Lifecycle.State#DESTROYED}状态,其它状态都会触发回调
     *                      为false则表示需要状态为{@link Lifecycle.State#STARTED}才会触发回调
     */
    public EventLiveData(int stickyCount, boolean activeForever) {
        mStickyCount = stickyCount;
        mActiveForever = activeForever;
    }

    private void decreaseStickyCount() {
        if (mStickyCount > 0) {
            mLeftStickCount--;
        }
    }

    private void afterNotify() {
        if (mStickyCount > 0 && mLeftStickCount < 1) {
            mData = NOT_SET;
        } else if (mStickyCount == SEND_ONCE) {
            if (mShouldStickyOnce) {
                if (mInSetValue) {
                    mShouldStickyOnce = false;
                } else {
                    mData = NOT_SET;
                }
            }
        }
    }

    private void afterSetValue() {
        if (mStickyCount == NO_STICKY) {
            mData = NOT_SET;
        } else if (mStickyCount == SEND_ONCE) {
            if (!mShouldStickyOnce) {
                mData = NOT_SET;
            }
        }
    }

    private void considerNotify(ObserverWrapper observer) {
        if (!observer.mActive) {
            return;
        }
        // Check latest state b4 dispatch. Maybe it changed state but we didn't get the event yet.
        //
        // we still first check observer.active to keep it as the entrance for events. So even if
        // the observer moved to an active state, if we've not received that event, we better not
        // notify for a more predictable notification order.
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        if (observer.mLastVersion >= mVersion || mData == NOT_SET) {
            return;
        }
        observer.mLastVersion = mVersion;
        //noinspection unchecked
        observer.mObserver.onChanged((T) mData);
        afterNotify();
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void dispatchingValue(@Nullable ObserverWrapper initiator) {
        if (mDispatchingValue) {
            mDispatchInvalidated = true;
            return;
        }
        mDispatchingValue = true;
        do {
            mDispatchInvalidated = false;
            if (initiator != null) {
                considerNotify(initiator);
                initiator = null;
            } else {
                for (Iterator<Map.Entry<Observer<? super T>, ObserverWrapper>> iterator =
                     mObservers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    considerNotify(iterator.next().getValue());
                    if (mDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }

    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        if (!isMainThread()) {
            postToMainThread(() -> {
                observe(owner, observer);
            });
            return;
        }
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            // ignore
            return;
        }
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
        ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        decreaseStickyCount();

        //避免刚注册时因为没有状态改变而导致事件不触发
        wrapper.activeStateChanged(wrapper.shouldBeActive());

        owner.getLifecycle().addObserver(wrapper);
    }

    /**
     * Adds the given observer to the observers list. This call is similar to
     * {@link EventLiveData#observe(LifecycleOwner, Observer)} with a LifecycleOwner, which
     * is always active. This means that the given observer will receive all events and will never
     * be automatically removed. You should manually call {@link #removeObserver(Observer)} to stop
     * observing this LiveData.
     * While LiveData has one of such observers, it will be considered
     * as active.
     * <p>
     * If the observer was already added with an owner to this LiveData, LiveData throws an
     * {@link IllegalArgumentException}.
     *
     * @param observer The observer that will receive the events
     */
    @MainThread
    public void observeForever(@NonNull Observer<? super T> observer) {
        if (!isMainThread()) {
            postToMainThread(() -> {
                observeForever(observer);
            });
            return;
        }
        AlwaysActiveObserver wrapper = new AlwaysActiveObserver(observer);
        ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
        if (existing != null && existing instanceof EventLiveData.LifecycleBoundObserver) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        decreaseStickyCount();
        wrapper.activeStateChanged(true);
    }

    /**
     * Removes the given observer from the observers list.
     *
     * @param observer The Observer to receive events.
     */
    @MainThread
    public void removeObserver(@NonNull final Observer<? super T> observer) {
        if (!isMainThread()) {
            postToMainThread(() -> {
                removeObserver(observer);
            });
            return;
        }
        ObserverWrapper removed = mObservers.remove(observer);
        if (removed == null) {
            return;
        }
        removed.detachObserver();
        removed.activeStateChanged(false);
    }

    /**
     * Removes all observers that are tied to the given {@link LifecycleOwner}.
     *
     * @param owner The {@code LifecycleOwner} scope for the observers to be removed.
     */
    @SuppressWarnings("WeakerAccess")
    @MainThread
    public void removeObservers(@NonNull final LifecycleOwner owner) {
        if (!isMainThread()) {
            postToMainThread(() -> {
                removeObservers(owner);
            });
            return;
        }
        for (Map.Entry<Observer<? super T>, ObserverWrapper> entry : mObservers) {
            if (entry.getValue().isAttachedTo(owner)) {
                removeObserver(entry.getKey());
            }
        }
    }

    /**
     * Posts a task to a main thread to set the given value. So if you have a following code
     * executed in the main thread:
     * <pre class="prettyprint">
     * liveData.postValue("a");
     * liveData.setValue("b");
     * </pre>
     * The value "b" would be set at first and later the main thread would override it with
     * the value "a".
     * <p>
     * If you called this method multiple times before a main thread executed a posted task, only
     * the last value would be dispatched.
     *
     * @param value The new value
     */
    public void postValue(T value) {
        boolean postTask;
        synchronized (mDataLock) {
            postTask = mPendingData == NOT_SET;
            mPendingData = value;
        }
        if (!postTask) {
            return;
        }
        postToMainThread(mPostValueRunnable);
    }

    /**
     * Sets the value. If there are active observers, the value will be dispatched to them.
     * <p>
     * This method must be called from the main thread. If you need set a value from a background
     * thread, you can use {@link #postValue(Object)}
     *
     * @param value The new value
     */
    @MainThread
    private void setValueInternal(T value) {
        mVersion++;
        mData = value;
        mInSetValue = true;
        try {
            if (mStickyCount > 0) {
                mLeftStickCount = mStickyCount;
            } else if (mStickyCount == SEND_ONCE) {
                mShouldStickyOnce = true;
            }
            dispatchingValue(null);
        } finally {
            mInSetValue = false;
        }
        afterSetValue();
    }


    public void setValue(T value) {
        setValueSync(this, value);
    }

    /**
     * Returns the current value.
     * Note that calling this method on a background thread does not guarantee that the latest
     * value set will be received.
     *
     * @return the current value
     */
    @Nullable
    public T getValue() {
        Object data = mData;
        if (data != NOT_SET) {
            //noinspection unchecked
            return (T) data;
        }
        return null;
    }

    int getVersion() {
        return mVersion;
    }


    protected void onActive() {

    }


    protected void onInactive() {

    }

    /**
     * Returns true if this LiveData has observers.
     *
     * @return true if this LiveData has observers
     */
    @SuppressWarnings("WeakerAccess")
    public boolean hasObservers() {
        return mObservers.size() > 0;
    }

    /**
     * Returns true if this LiveData has active observers.
     *
     * @return true if this LiveData has active observers
     */
    @SuppressWarnings("WeakerAccess")
    public boolean hasActiveObservers() {
        return mActiveCount > 0;
    }

    class LifecycleBoundObserver extends ObserverWrapper implements LifecycleEventObserver {
        @NonNull
        final LifecycleOwner mOwner;

        LifecycleBoundObserver(@NonNull LifecycleOwner owner, Observer<? super T> observer) {
            super(observer);
            mOwner = owner;
        }

        @Override
        boolean shouldBeActive() {
            return (mActiveForever && mOwner.getLifecycle().getCurrentState() != DESTROYED) || mOwner.getLifecycle().getCurrentState().isAtLeast(STARTED);
        }

        @Override
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(mObserver);
                return;
            }
            activeStateChanged(shouldBeActive());
        }

        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return mOwner == owner;
        }

        @Override
        void detachObserver() {
            mOwner.getLifecycle().removeObserver(this);
        }
    }

    private abstract class ObserverWrapper {
        final Observer<? super T> mObserver;
        boolean mActive;
        int mLastVersion = START_VERSION;

        ObserverWrapper(Observer<? super T> observer) {
            mObserver = observer;
        }

        abstract boolean shouldBeActive();

        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }

        void detachObserver() {
        }

        void activeStateChanged(boolean newActive) {
            if (newActive == mActive) {
                return;
            }
            // immediately set active state, so we'd never dispatch anything to inactive
            // owner
            mActive = newActive;
            boolean wasInactive = EventLiveData.this.mActiveCount == 0;
            EventLiveData.this.mActiveCount += mActive ? 1 : -1;
            if (wasInactive && mActive) {
                onActive();
            }
            if (EventLiveData.this.mActiveCount == 0 && !mActive) {
                onInactive();
            }
            if (mActive) {
                dispatchingValue(this);
            }
        }
    }

    private class AlwaysActiveObserver extends ObserverWrapper {

        AlwaysActiveObserver(Observer<? super T> observer) {
            super(observer);
        }

        @Override
        boolean shouldBeActive() {
            return true;
        }
    }

    private static void assertMainThread(String methodName) {
        if (!isMainThread()) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a background"
                    + " thread");
        }
    }


    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static void postToMainThread(Runnable runnable) {
        handler.post(runnable);
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    private static <T> void setValueSync(EventLiveData<T> liveData, T value) {
        if (liveData == null) {
            return;
        }
        if (isMainThread()) {
            liveData.setValueInternal(value);
            return;
        }
        Runnable runnable = () -> {
            synchronized (liveData) {
                try {
                    liveData.setValueInternal(value);
                } finally {
                    liveData.notify();
                }
            }
        };
        synchronized (liveData) {
            postToMainThread(runnable);
            try {
                liveData.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
