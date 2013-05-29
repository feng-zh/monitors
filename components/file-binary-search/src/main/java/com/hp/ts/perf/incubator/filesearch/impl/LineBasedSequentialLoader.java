package com.hp.ts.perf.incubator.filesearch.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.hp.ts.perf.incubator.filesearch.ContentBlock;
import com.hp.ts.perf.incubator.filesearch.ContentBlockDetector;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSearchStatistic;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSearchStatistic.Statistic;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSequentialAccess;

public class LineBasedSequentialLoader implements ContentBlockSequentialAccess {

	private ContentBlockDetector detector;
	private InputStream input;
	private long offset;
	private ContentBlock nextStartBlock;
	private byte[] pushback;
	private ContentBlockSearchStatistic statistic = new ContentBlockSearchStatistic();

	public LineBasedSequentialLoader(InputStream input,
			ContentBlockDetector detector) {
		this.input = new BufferedInputStream(input);
		this.detector = detector;
		this.offset = 0;
	}

	@Override
	public ContentBlock nextBlock() throws IOException {
		Statistic stat = statistic.searchStat();
		stat.start();
		try {
			return nextBlock0();
		} finally {
			stat.incCount();
		}
	}

	private ContentBlock nextBlock0() throws IOException {
		LinkedContentBlock blocks = new LinkedContentBlock();
		while (true) {
			ContentBlock block = null;
			if (nextStartBlock != null) {
				block = nextStartBlock;
				nextStartBlock = null;
			} else {
				block = nextLineBlock();
			}
			if (!blocks.isEmpty()) {
				if (block == null || isBlockStart(block)) {
					nextStartBlock = block;
					return blocks;
				}
			}
			if (block == null) {
				// EOF
				return null;
			} else {
				blocks.addLast(block);
			}
		}
	}

	private boolean isBlockStart(ContentBlock block) {
		Statistic stat = statistic.detectStat();
		stat.start();
		try {
			return detector.isStartBlock(block);
		} finally {
			stat.incCount();
		}
	}

	private ContentBlock nextLineBlock() throws IOException {
		LinkedContentBlock blocks = new LinkedContentBlock();
		Statistic stat = statistic.readStat();
		while (true) {
			// suppose most line is less than 1024
			byte[] buffer = new byte[1024];
			int len = 0;
			if (pushback != null) {
				System.arraycopy(pushback, 0, buffer, 0, pushback.length);
				len = pushback.length;
				pushback = null;
			} else {
				stat.start();
				try {
					len = input.read(buffer);
				} finally {
					stat.incBytes(len);
				}
			}
			if (len == -1) {
				if (blocks.isEmpty()) {
					return null;
				} else {
					return blocks;
				}
			}
			ContentBlock block = null;
			for (int index = 0; index < len; index++) {
				if (buffer[index] == '\n') {
					index++;
					block = new BytesContentBlock(offset, buffer, index);
					offset += block.getLength();
					// push back bytes
					if (index < len) {
						pushback = Arrays.copyOfRange(buffer, index, len);
					}
					blocks.addLast(block);
					return blocks;
				}
			}
			// no eol found
			block = new BytesContentBlock(offset, buffer, len);
			offset += block.getLength();
			blocks.addLast(block);
		}
	}

	@Override
	public ContentBlockSearchStatistic getStatistic() {
		return statistic;
	}
}
