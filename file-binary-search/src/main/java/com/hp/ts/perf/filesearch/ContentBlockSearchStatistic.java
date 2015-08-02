package com.hp.ts.perf.filesearch;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class ContentBlockSearchStatistic {

	public enum Type {
		Access, Read, Detect, Match, Search
	}

	public static class Statistic {
		private int count;
		private long duration;
		private long bytes;

		private long startTime;

		public void start() {
			startTime = System.nanoTime();
		}

		public void incCount() {
			duration += System.nanoTime() - startTime;
			count++;
		}

		public void incBytes(int len) {
			duration += System.nanoTime() - startTime;
			// ignore -1
			if (len > 0) {
				bytes += len;
			}
		}

		public int getCount() {
			return count;
		}

		public long getDuration() {
			return duration;
		}

		public long getBytes() {
			return bytes;
		}

		@Override
		public String toString() {
			DecimalFormat format = new DecimalFormat("#0.#");
			String durationTime = format.format(TimeUnit.NANOSECONDS
					.toMicros(duration) / 1000.0);
			if (count > 0) {
				return String.format("count=%s, duration=%s ms", count,
						durationTime);
			} else {
				return String.format("bytes=%s, duration=%s ms", bytes,
						durationTime);
			}
		}

		boolean isEmpty() {
			return duration == 0;
		}

	}

	private Statistic[] array = new Statistic[Type.values().length];

	{
		for (int i = 0; i < array.length; i++) {
			array[i] = new Statistic();
		}
	}

	final private Statistic statistic(Type type) {
		return array[type.ordinal()];
	}

	final public Statistic accessStat() {
		return statistic(Type.Access);
	}

	final public Statistic detectStat() {
		return statistic(Type.Detect);
	}

	final public Statistic readStat() {
		return statistic(Type.Read);
	}

	final public Statistic searchStat() {
		return statistic(Type.Search);
	}

	final public Statistic matchStat() {
		return statistic(Type.Match);
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		for (Type type : Type.values()) {
			Statistic stat = statistic(type);
			if (!stat.isEmpty()) {
				buffer.append(type.name().toLowerCase()).append("[")
						.append(stat).append("] ");
			}
		}
		return buffer.toString();
	}

}
