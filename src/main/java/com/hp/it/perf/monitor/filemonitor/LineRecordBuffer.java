package com.hp.it.perf.monitor.filemonitor;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class LineRecordBuffer implements Runnable {

	private int size;

	private Queue<LineRecord> queue = new LinkedList<LineRecord>();

	private long lastTime = System.nanoTime();

	private BufferHandler handler;

	private long bufTime;

	private ScheduledExecutorService scheduler;

	private ScheduledFuture<?> scheduledFuture;

	public static interface BufferHandler {

		public void handleBuffer(Queue<LineRecord> buffer);

	}

	public LineRecordBuffer(int size, int time, BufferHandler handler,
			ScheduledExecutorService scheduler) {
		this.size = size;
		this.bufTime = TimeUnit.MILLISECONDS.toNanos(time);
		this.handler = handler;
		this.scheduler = scheduler;
	}

	public synchronized void add(LineRecord item) {
		long now = System.nanoTime();
		if (queue.isEmpty()) {
			lastTime = now;
			scheduledFuture = scheduler.schedule(this, bufTime,
					TimeUnit.NANOSECONDS);
		}
		queue.offer(item);
		if (queue.size() >= size || (now - lastTime) > bufTime) {
			// over size limitation or exceed buffer time
			flush();
		}
	}

	@Override
	public synchronized void run() {
		scheduledFuture = null;
		if (!queue.isEmpty()) {
			handler.handleBuffer(queue);
		}
	}

	public synchronized void flush() {
		if (scheduledFuture != null) {
			scheduledFuture.cancel(false);
		}
		run();
	}
}
