package com.yangfei.smrtenglish.util.unpeeklivedata;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 唯一可信源设计：确保 "事件" 发送权牢牢握在可信逻辑中枢手里。
 * 相比普通 LiveData，解决 "数据倒灌" 问题。
 */
public class ProtectedUnPeekLiveData<T> extends LiveData<T> {

    public ProtectedUnPeekLiveData(T value) {
        super(value);
    }

    public ProtectedUnPeekLiveData() {
        super();
    }

    private final static int START_VERSION = -1;

    private final AtomicInteger mCurrentVersion = new AtomicInteger(START_VERSION);

    protected boolean isAllowNullValue;

    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, createObserverWrapper(observer, mCurrentVersion.get()));
    }

    @Override
    public void observeForever(@NonNull Observer<? super T> observer) {
        super.observeForever(createObserverWrapper(observer, mCurrentVersion.get()));
    }

    public void observeSticky(@NonNull LifecycleOwner owner, @NonNull Observer<T> observer) {
        super.observe(owner, createObserverWrapper(observer, START_VERSION));
    }

    public void observeStickyForever(@NonNull Observer<? super T> observer) {
        super.observeForever(createObserverWrapper(observer, START_VERSION));
    }

    @Override
    protected void setValue(T value) {
        mCurrentVersion.getAndIncrement();
        super.setValue(value);
    }

    class ObserverWrapper implements Observer<T> {
        private final Observer<? super T> mObserver;
        private int mVersion = START_VERSION;

        public ObserverWrapper(@NonNull Observer<? super T> observer, int version) {
            this.mObserver = observer;
            this.mVersion = version;
        }

        @Override
        public void onChanged(T t) {
            if (mCurrentVersion.get() > mVersion && (t != null || isAllowNullValue)) {
                mObserver.onChanged(t);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObserverWrapper that = (ObserverWrapper) o;
            return Objects.equals(mObserver, that.mObserver);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mObserver);
        }
    }

    @Override
    public void removeObserver(@NonNull Observer<? super T> observer) {
        if (observer.getClass().isAssignableFrom(ObserverWrapper.class)) {
            super.removeObserver(observer);
        } else {
            super.removeObserver(createObserverWrapper(observer, START_VERSION));
        }
    }

    private ObserverWrapper createObserverWrapper(@NonNull Observer<? super T> observer, int version) {
        return new ObserverWrapper(observer, version);
    }

    public void clean() {
        super.setValue(null);
    }
}
