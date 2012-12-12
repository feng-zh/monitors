package com.hp.it.perf.monitor.filemonitor;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ContentUpdateChecker implements Observer {

	private volatile long lastTickCount;

	private final ContentUpdateObservable observable;

	private final Lock lock = new ReentrantLock();

	private final Condition sync = lock.newCondition();

	// package-private
	ContentUpdateChecker(ContentUpdateObservable observable) {
		this.observable = observable;
	}

	@Override
	public void update(Observable o, Object arg) {
		if (o != observable) {
			// not my observable (as this is added to other observable)
			return;
		}
		lock.lock();
		try {
			lastTickCount = (Long) arg;
			sync.signalAll();
		} finally {
			lock.unlock();
		}
	}

	public void await(long tickCount) throws InterruptedException {
		lock.lock();
		try {
			while (lastTickCount <= tickCount) {
				sync.await();
			}
		} finally {
			lock.unlock();
		}
	}

	public boolean await(long tickCount, long timeout, TimeUnit unit)
			throws InterruptedException {
		long nanosTimeout = unit.toNanos(timeout);
		lock.lock();
		try {
			while (lastTickCount <= tickCount) {
				if (nanosTimeout <= 0L)
					return false;
				nanosTimeout = sync.awaitNanos(nanosTimeout);
			}
		} finally {
			lock.unlock();
		}
		return true;
	}

	public long getLastTickCount() {
		return lastTickCount;
	}

}
