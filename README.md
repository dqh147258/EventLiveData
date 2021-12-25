# A event dispatcher for Android.

[中文简介](https://www.jianshu.com/p/c7e3ddcb14be)

[![](https://www.jitpack.io/v/dqh147258/EventLiveData.svg)](https://www.jitpack.io/#dqh147258/EventLiveData)

- You can simply use it similer to LiveData.
- You can safely use it on background thread, and it will call observer on main thread.

## Dependencies
```
	allprojects {
		repositories {
			//...
			maven { url 'https://www.jitpack.io' }
		}
	}
```
```

	dependencies {
	        implementation 'com.github.dqh147258:EventLiveData:1.0.+'
	}
```

## How to use
For example:

First, create a singleton object like EventPool to hold the EventLiveData.

```kotlin
object EventPool {

    val userInfoUpdateEvent = EventLiveData<Boolean>(STICKY_FOREVER, false)

    val userInfoShouldUpdateEvent = EventLiveData<Boolean>(SEND_ONCE, false)
    
    //......
}

```

Second, register your observer in suitable place.

```kotlin
    EventPool.userInfoShouldUpdateEvent.observe(owner) {
        getUserInfo()
    }
```

Third, set value when you want to send a event.
```kotlin
    EventPool.userInfoShouldUpdateEvent.value = true
```

That all.

## The parameters of EventLiveData's constructor
The constructor of EventLiveData has two parameters:

stickyCount(Int) and activeForever(Boolean)

### stickyCount

If stickyCount more than zero, the meaning of stickyCount is the left count for sending sticky event.

If stickyCount value is `EventLiveData.NO_STICKY` means that the event of this EventLiveData would send is not sticky event.

If stickyCount value is `EventLiveData.STICKY_FOREVER` means that the event of this EventLiveData would send is sticky event.

If stickyCount value is `EventLiveData.SEND_ONCE` means that the event of this EventLiveData would send just will send once.

### activeForever

Whether the event should be send or not while the LifCycleOwner which subscribed this EventLiveData is in invisiable state.







