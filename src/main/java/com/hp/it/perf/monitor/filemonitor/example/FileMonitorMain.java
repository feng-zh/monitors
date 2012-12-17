package com.hp.it.perf.monitor.filemonitor.example;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import com.hp.it.perf.monitor.filemonitor.CompositeContentProvider;
import com.hp.it.perf.monitor.filemonitor.FileContentInfo;
import com.hp.it.perf.monitor.filemonitor.FileContentProvider;
import com.hp.it.perf.monitor.filemonitor.FileMonitorService;
import com.hp.it.perf.monitor.filemonitor.FolderContentProvider;
import com.hp.it.perf.monitor.filemonitor.LineRecord;
import com.hp.it.perf.monitor.filemonitor.UniqueFile;
import com.hp.it.perf.monitor.filemonitor.nio.NioFileMonitorService;

public class FileMonitorMain {

	private static FileFilter textFilter = new FileFilter() {

		@Override
		public boolean accept(File pathname) {
			if (pathname.isDirectory())
				return false;
			String contentType = URLConnection.getFileNameMap()
					.getContentTypeFor(pathname.toString());
			return (contentType != null && (contentType.startsWith("text/") || contentType
					.endsWith("/xml")));
		}
	};

	private static Map<Long, FileContentInfo> infos = new HashMap<Long, FileContentInfo>();

	/**
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException,
			InterruptedException {
		if (args.length==0) {
			args = new String[]{"."};
		}
		FileMonitorService monitorService = new NioFileMonitorService();
		CompositeContentProvider suite = new CompositeContentProvider();
		for (int i = 0; i < args.length; i++) {
			File file = new File(args[i]);
			if (!file.canRead()) {
				continue;
			}
			if (file.isDirectory()) {
				FolderContentProvider provider = new FolderContentProvider();
				provider.setFolder(file);
				provider.setMonitorService(monitorService);
				provider.setTailMode(true);
				provider.setFilter(textFilter);
				suite.getProviders().add(provider);
			} else {
				UniqueFile provider = new UniqueFile();
				provider.setFile(file);
				provider.setMonitorService(monitorService);
				suite.getProviders().add(provider);
			}
		}
		suite.init();
		LineRecord line;
		refreshFiles(suite);
		while ((line = suite.readLine()) != null) {
			System.out.print(getFileName(line.getProviderId(), suite) + ":"
					+ new String(line.getLine(), "UTF-8"));
		}
		suite.close();
	}

	private static void refreshFiles(FileContentProvider suite)
			throws IOException {
		for (FileContentInfo content : suite.getFileContentInfos(false)) {
			infos.put(content.getProviderId(), content);
		}
	}

	private static String getFileName(long providerId, FileContentProvider suite)
			throws IOException {
		FileContentInfo info = infos.get(providerId);
		if (info == null) {
			refreshFiles(suite);
		}
		info = infos.get(providerId);
		if (info == null) {
			return "[UNKOWN FILE]";
		} else {
			return info.getCurrentFileName();
		}
	}

}
