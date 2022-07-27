package com.yxf.eventlivedata;

import static com.yxf.eventlivedata.AutoRemoveObserverManager.MapContainer.getMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public interface AutoRemoveObserverManager {


    default void addAutoRemoveObserver(EventLiveData.AutoRemoveObserver observer) {
        getMap(this).add(observer);
    }

    default void removeAutoRemoveObserver(EventLiveData.AutoRemoveObserver observer) {
        getMap(this).remove(observer);
    }

    default boolean containObserver(EventLiveData.AutoRemoveObserver observer) {
        return getMap(this).contains(observer);
    }

    default void clearAllObserver() {
        Set set = getMap(this);
        Iterator<EventLiveData.AutoRemoveObserver> iterator = set.iterator();
        while (iterator.hasNext()) {
            EventLiveData.AutoRemoveObserver observer = iterator.next();
            iterator.remove();
            observer.remove();
        }

    }

    class MapContainer {

        private static Map<AutoRemoveObserverManager, Set<EventLiveData.AutoRemoveObserver>> map = new HashMap();

        static Set<EventLiveData.AutoRemoveObserver> getMap(AutoRemoveObserverManager manager) {
            Set set = map.get(manager);
            if (set == null) {
                set = new HashSet();
                map.put(manager, set);
            }
            return set;
        }

        static void removeMap(AutoRemoveObserverManager manager) {
            map.remove(manager);
        }


    }


}
