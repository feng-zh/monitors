package com.hp.it.perf.monitor.filemonitor;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

class RecordedQueue<T> extends AbstractQueue<T> {
	private final Queue<T> list;
	private final Queue<T> recorded = new LinkedList<T>();

	RecordedQueue(Queue<T> list) {
		this.list = list;
	}

	@Override
	public T poll() {
		return list.poll();
	}

	@Override
	public T peek() {
		return list.peek();
	}

	@Override
	public boolean offer(T e) {
		boolean ret = list.offer(e);
		if (ret) {
			recorded.offer(e);
		}
		return ret;
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public Iterator<T> iterator() {
		return list.iterator();
	}

	public Queue<T> getRecorded() {
		return recorded;
	}
}