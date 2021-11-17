package com.yxf.eventlivedatasample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yxf.eventlivedata.EventLiveData
import com.yxf.eventlivedata.EventLiveData.NO_STICKY
import com.yxf.eventlivedata.EventLiveData.STICKY_FOREVER

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val stickyEvent = EventLiveData<Int>()
    private val noStickyEvent = EventLiveData<Int>(NO_STICKY, true)
    private val stickyCountEvent = EventLiveData<Int>(1, true)
    private val activeForeverEvent = EventLiveData<Int>(STICKY_FOREVER, true)
    private val notForeverEvent = EventLiveData<Int>(STICKY_FOREVER, false)

    private val TAG = "EventLiveData"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        testStickyEvent()
        testNoStickyEvent()
        testStickyCount()
        testActiveForever()
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