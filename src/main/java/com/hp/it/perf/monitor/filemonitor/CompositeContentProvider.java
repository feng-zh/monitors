package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class CompositeContentProvider implements FileContentProvider {

	private List<FileContentProvider> providers = new ArrayList<FileContentProvider>();

	private ContentUpdateObserver contentUpdateObserver = new ContentUpdateObserver();

	public List<FileContentProvider> getProviders() {
		return providers;
	}

	public void setProviders(List<FileContentProvider> providers) {
		this.providers = providers;
	}

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int readLines(Queue<LineRecord> list, int maxSize)
			throws IOException {
		int totalLen = 0;
		for (FileContentProvider provider : providers) {
			int len = provider.readLines(list, maxSize);
			if (len != -1) {
				maxSize -= len;
				totalLen += len;
			}
		}
		return totalLen;
	}

	@Override
	public void init() throws IOException {
		for (FileContentProvider provider : providers) {
			provider.addUpdateObserver(contentUpdateObserver);
			provider.init();
		}
	}

	@Override
	public void close() throws IOException {
		for (FileContentProvider provider : providers) {
			provider.removeUpdateObserver(contentUpdateObserver);
			provider.close();
		}
	}

	@Override
	public List<FileContentInfo> getFileContentInfos() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addUpdateObserver(Observer observer) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		// TODO Auto-generated method stub

	}

}
