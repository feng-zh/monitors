package com.hp.ts.perf.filesearch.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

import com.hp.ts.perf.filesearch.ContentBlock;

class LinkedContentBlock implements ContentBlock {

	private LinkedList<ContentBlock> list = new LinkedList<ContentBlock>();

	@Override
	public long getStart() {
		return list.getFirst().getStart();
	}

	@Override
	public int getLength() {
		int len = 0;
		for (ContentBlock block : list) {
			len += block.getLength();
		}
		return len;
	}

	@Override
	public long getEnd() {
		return getLast().getEnd();
	}

	@Override
	public byte[] toBytes() {
		if (list.size() == 1) {
			return list.getFirst().toBytes();
		} else {
			ByteArrayOutputStream output = new ByteArrayOutputStream(
					getLength());
			try {
				writeTo(output);
			} catch (IOException never) {
			}
			return output.toByteArray();
		}
	}

	@Override
	public void writeTo(OutputStream output) throws IOException {
		for (ContentBlock block : list) {
			block.writeTo(output);
		}
	}

	public void addFirst(ContentBlock block) {
		if (!list.isEmpty()) {
			if (list.getFirst().getStart() != block.getEnd()) {
				throw new IllegalStateException("not continues block");
			}
		}
		if (block instanceof LinkedContentBlock) {
			LinkedContentBlock lBlock = (LinkedContentBlock) block;
			for (Iterator<ContentBlock> iter = lBlock.list.descendingIterator(); iter
					.hasNext();) {
				addFirst(iter.next());
			}
		} else {
			list.addFirst(block);
		}
	}

	public ContentBlock getLast() {
		return list.getLast();
	}

	public void addLast(ContentBlock block) {
		if (!list.isEmpty()) {
			if (list.getLast().getEnd() != block.getStart()) {
				throw new IllegalStateException("not continues block");
			}
		}
		if (block instanceof LinkedContentBlock) {
			LinkedContentBlock lBlock = (LinkedContentBlock) block;
			for (ContentBlock cb : lBlock.list) {
				addLast(cb);
			}
		} else {
			list.addLast(block);
		}
	}

	public String toString() {
		return new String(toBytes());
	}

	@Override
	public int read(byte[] bytes, int offset, int len) {
		int total = 0;
		Iterator<ContentBlock> iter = list.iterator();
		while (len > 0 && iter.hasNext()) {
			int size = iter.next().read(bytes, offset, len);
			total += size;
			len -= size;
			offset += size;
		}
		return total;
	}

	@Override
	public int indexOf(byte b) {
		int total = 0;
		Iterator<ContentBlock> iter = list.iterator();
		while (iter.hasNext()) {
			ContentBlock block = iter.next();
			int index = block.indexOf(b);
			if (index < 0) {
				total += block.getLength();
			} else {
				total += index;
				return total;
			}
		}
		return -1;
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty() || getLength() == 0;
	}

	public int getBlocks() {
		return list.size();
	}

}
