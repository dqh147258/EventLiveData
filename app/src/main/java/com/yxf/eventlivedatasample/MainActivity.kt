package com.yxf.eventlivedatasample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yxf.eventlivedata.AutoRemoveObserverManager
import com.yxf.eventlivedata.AutoRemoveObserverManagerDelegate
import com.yxf.eventlivedata.EventLiveData
import com.yxf.eventlivedata.EventLiveData.*


class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val stickyEvent = EventLiveData<Int>()
    private val noStickyEvent = EventLiveData<Int>(NO_STICKY, true)
    private val stickyCountEvent = EventLiveData<Int>(1, true)
    private val activeForeverEvent = EventLiveData<Int>(STICKY_FOREVER, true)
    private val notForeverEvent = EventLiveData<Int>(STICKY_FOREVER, false)
    private val sendOnceEvent = EventLiveData<Int>(SEND_ONCE, false)
    private val sendOnceEvent2 = EventLiveData<Int>(SEND_ONCE, true)
    private val autoRemoveEvent = EventLiveData<Int>().apply { value = 0 }
    private val autoRemoveObserverManager = AutoRemoveObserverManagerDelegate()
    private val TAG = "EventLiveData"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        testStickyEvent()
        testNoStickyEvent()
        testStickyCount()
        testActiveForever()
        testSendOnce()
        testAutoRemove()
    }

    private fun testAutoRemove() {
        autoRemoveEvent.observe(autoRemoveObserverManager) {
            Log.d(TAG, "testAutoRemove: the message would not trigger after onPause, value: $it")
        }
    }

    override fun onPause() {
        super.onPause()
        autoRemoveObserverManager.clearAllObserver()
        autoRemoveEvent.value = 1
    }

    private fun testSendOnce() {
        sendOnceEvent.observe(this) {
            Log.d(TAG, "testSendOnce: event one, first observer, state: ${lifecycle.currentState}")
        }
        sendOnceEvent.value = 1
        sendOnceEvent.observe(this) {
            Log.d(TAG, "testSendOnce: event one ,second observer, state: ${lifecycle.currentState}")
        }
        sendOnceEvent2.observe(this) {
            Log.d(TAG, "testSendOnce: event two, first observer, state: ${lifecycle.currentState}")
        }
        sendOnceEvent2.value = 1
        sendOnceEvent2.observe(this) {
            Log.d(TAG, "testSendOnce: event two, second observer, state: ${lifecycle.currentState}")
        }
    }

    private fun testActiveForever() {
        activeForeverEvent.observe(this) {
            Log.d(TAG, "testActiveForever: it will be called in onCreate, state: ${lifecycle.currentState}")
        }
        notForeverEvent.observe(this) {
            Log.d(TAG, "testActiveForever: it will be called in onStart, state: ${lifecycle.currentState}")
        }
        activeForeverEvent.value = 1
        notForeverEvent.value = 1
    }

    private fun testStickyCount() {
        stickyCountEvent.observe(this) {
            Log.d(TAG, "testStickyCount: register before set, value: $it")
        }
        stickyCountEvent.value = 1
        stickyCountEvent.observe(this) {
            Log.d(TAG, "testStickyCount: first sticky event value: $it")
        }
        stickyCountEvent.observe(this) {
            Log.d(TAG, "testStickyCount: second sticky event value: $it")
        }
    }

    private fun testNoStickyEvent() {
        noStickyEvent.observe(this) {
            Log.d(TAG, "testNoStickyEvent: register before set, value: $it")
        }
        noStickyEvent.value = 0
        noStickyEvent.observe(this) {
            Log.d(TAG, "testNoStickyEvent: register after set, value: $it")
        }
    }

    private fun testStickyEvent() {
        Thread {
            stickyEvent.value = -1
            handler.post {
                stickyEvent.observe(this) {
                    Log.d(TAG, "testStickyEvent: get stick value: $it")
                }
            }
        }.start()
    }
}