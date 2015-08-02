package com.hp.ts.perf.filesearch.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.hp.ts.perf.filesearch.ContentBlock;
import com.hp.ts.perf.filesearch.ContentBlockDetector;
import com.hp.ts.perf.filesearch.ContentBlockRandomAccess;
import com.hp.ts.perf.filesearch.ContentBlockSearchStatistic;
import com.hp.ts.perf.filesearch.ContentBlockSearchStatistic.Statistic;

public class LineBasedFileLoader implements ContentBlockRandomAccess {

	private static int BLOCK_SIZE = 1024;

	private RandomAccessFile file;

	private long length;

	private static byte[] eol = new byte[] { '\n' };

	private ContentBlockDetector detector;

	private ContentBlockSearchStatistic statistic = new ContentBlockSearchStatistic();

	private Map<Long, Boolean> lineOffsets = new LinkedHashMap<Long, Boolean>() {

		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Entry<Long, Boolean> eldest) {
			return size() >= 1024;
		}

	};

	public LineBasedFileLoader(RandomAccessFile file,
			ContentBlockDetector detector) throws IOException {
		this.file = file;
		this.detector = detector;
		Statistic access = statistic.accessStat();
		access.start();
		try {
			this.length = file.length();
		} finally {
			access.incCount();
		}
	}

	@Override
	public ContentBlock locateBlock(long position) throws IOException {
		Statistic stat = statistic.searchStat();
		stat.start();
		try {
			return locateBlock0(position);
		} finally {
			stat.incCount();
		}
	}

	private ContentBlock locateBlock0(long position) throws IOException {
		if (position < 0 || position >= length) {
			throw new IOException("out of size: " + position + " (" + length
					+ ")");
		}
		Statistic stat = statistic.accessStat();
		stat.start();
		try {
			file.seek(position);
		} finally {
			stat.incCount();
		}
		long current = position;
		LinkedContentBlock blocks = new LinkedContentBlock();
		while (current < length && current > 0) {
			// inside of line
			current = processBackwards(current);
			// move to next to EOL
			stat.start();
			try {
				file.seek(current);
			} finally {
				stat.incCount();
			}
			ContentBlock block = processCurrentLine(current);
			blocks.addFirst(block);
			if (current > 0 && !isBlockStart(block)) {
				current--;
				continue;
			} else {
				current = blocks.getLast().getEnd();
				break;
			}
		}
		while (current < length) {
			// move to next to EOL
			stat.start();
			try {
				file.seek(current);
			} finally {
				stat.incCount();
			}
			ContentBlock block = processCurrentLine(current);
			if (isBlockStart(block)) {
				break;
			}
			blocks.addLast(block);
			current = block.getEnd();
		}
		if (blocks.getBlocks() == 0) {
			blocks.addFirst(new BytesContentBlock(position, new byte[0], 0));
		}
		return blocks;
	}

	private boolean isBlockStart(ContentBlock block) {
		Boolean startBlock = lineOffsets.get(block.getStart());
		if (startBlock == null) {
			Statistic stat = statistic.detectStat();
			stat.start();
			try {
				startBlock = detector.isStartBlock(block);
			} finally {
				stat.incCount();
			}
			lineOffsets.put(block.getStart(), startBlock);
			return startBlock.booleanValue();
		} else {
			return startBlock.booleanValue();
		}
	}

	public long length() {
		return length;
	}

	private long processBackwards(long current) throws IOException {
		byte[] buf = new byte[BLOCK_SIZE];
		int backCounts = 0;
		// process current line or lines before
		// look backward for EOL
		int len = buf.length;
		Statistic stat = statistic.accessStat();
		MAIN_LOOP: while (current > 0) {
			len = (int) Math.min(current, len);
			// move backward
			current -= len;
			stat.start();
			try {
				file.seek(current);
			} finally {
				stat.incCount();
			}
			int fromIndex = len;
			len = readFully(buf, 0, len);
			int eolIndex;
			while ((eolIndex = lastIndexOf(buf, 0, len, eol, fromIndex)) != -1) {
				// Find EOL in back block
				// now, it is next to previous EOL
				if (backCounts < 0) {
					backCounts++;
					// move back an EOL-1
					fromIndex = eolIndex - 1;
				} else {
					// all done (back count is 0, found eol)
					current += eolIndex + eol.length;
					break MAIN_LOOP;
				}
			}
			if (eolIndex == -1 && current == 0) {
				// in head of file
				break;
			}
			if (eolIndex == -1 && backCounts < 0) {
				// No EOL in back block
				// move back
				int keepLen = eol.length;
				len = len - keepLen;
				System.arraycopy(buf, 0, buf, len, keepLen);
			}
		}
		return current;
	}

	private ContentBlock processCurrentLine(long current) throws IOException {
		byte[] buf = new byte[BLOCK_SIZE];
		// read until EOL or EOF
		int fromIndex = 0;
		int len;
		len = readFully(buf, 0, buf.length);
		boolean reachEOF = false;
		while (len != -1) {
			// search EOL from buf [0..len)
			int eolIndex = indexOf(buf, 0, len, eol, fromIndex);
			if (eolIndex != -1 || reachEOF) {
				// found EOL in current read block
				// or reach EOF
				if (reachEOF && eolIndex == -1) {
					eolIndex = len;
					return new BytesContentBlock(current, buf, eolIndex);
				} else {
					return new BytesContentBlock(current, buf, eolIndex
							+ eol.length);
				}
			} else {
				// no EOL in current block
				// try to read more
				byte[] newBuf = new byte[(int) (buf.length * 1.5)];
				System.arraycopy(buf, 0, newBuf, 0, len);
				buf = newBuf;
				fromIndex = len - eol.length;
				int required = buf.length - len;
				int newLen = readFully(buf, len, required);
				if (newLen == -1) {
					// reach EOF
					return new BytesContentBlock(current, buf, len);
				} else if (newLen < required) {
					// reach EOF, current Line is end of file
					len += newLen;
					reachEOF = true;
				} else {
					// all fully read, continue
					len += newLen;
				}
			}
		}
		// reach EOF
		return null;
	}

	// -------------------------------------
	private int readFully(byte[] buf, int off, int len) throws IOException {
		int n = 0;
		Statistic stat = statistic.readStat();
		do {
			int count = 0;
			stat.start();
			try {
				count = file.read(buf, off + n, len - n);
			} finally {
				stat.incBytes(count);
			}
			if (count < 0) {
				if (n == 0) {
					return -1;
				} else {
					break;
				}
			}
			n += count;
		} while (n < len);
		return n;
	}

	static int indexOf(byte[] source, byte[] target, int fromIndex) {
		return indexOf(source, 0, source.length, target, fromIndex);
	}

	static int indexOf(byte[] source, int sourceOffset, int sourceCount,
			byte[] target, int fromIndex) {
		int targetOffset = 0;
		int targetCount = target.length;
		if (fromIndex >= sourceCount) {
			return (targetCount == 0 ? sourceCount : -1);
		}
		if (fromIndex < 0) {
			fromIndex = 0;
		}
		if (targetCount == 0) {
			return fromIndex;
		}

		byte first = target[targetOffset];
		int max = sourceOffset + (sourceCount - targetCount);

		for (int i = sourceOffset + fromIndex; i <= max; i++) {
			/* Look for first character. */
			if (source[i] != first) {
				while (++i <= max && source[i] != first)
					;
			}

			/* Found first character, now look at the rest of v2 */
			if (i <= max) {
				int j = i + 1;
				int end = j + targetCount - 1;
				for (int k = targetOffset + 1; j < end
						&& source[j] == target[k]; j++, k++)
					;

				if (j == end) {
					/* Found whole string. */
					return i - sourceOffset;
				}
			}
		}
		return -1;
	}

	static int lastIndexOf(byte[] source, byte[] target, int fromIndex) {
		return lastIndexOf(source, 0, source.length, target, fromIndex);
	}

	static int lastIndexOf(byte[] source, int sourceOffset, int sourceCount,
			byte[] target, int fromIndex) {
		int targetOffset = 0;
		int targetCount = target.length;
		int rightIndex = sourceCount - targetCount;
		if (fromIndex < 0) {
			return -1;
		}
		if (fromIndex > rightIndex) {
			fromIndex = rightIndex;
		}
		/* Empty string always matches. */
		if (targetCount == 0) {
			return fromIndex;
		}

		int strLastIndex = targetOffset + targetCount - 1;
		byte strLastChar = target[strLastIndex];
		int min = sourceOffset + targetCount - 1;
		int i = min + fromIndex;

		startSearchForLastChar: while (true) {
			while (i >= min && source[i] != strLastChar) {
				i--;
			}
			if (i < min) {
				return -1;
			}
			int j = i - 1;
			int start = j - (targetCount - 1);
			int k = strLastIndex - 1;

			while (j > start) {
				if (source[j--] != target[k--]) {
					i--;
					continue startSearchForLastChar;
				}
			}
			return start - sourceOffset + 1;
		}
	}

	@Override
	public ContentBlockSearchStatistic getStatistic() {
		return statistic;
	}
}
