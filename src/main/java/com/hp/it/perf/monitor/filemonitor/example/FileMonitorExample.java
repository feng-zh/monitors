package com.hp.it.perf.monitor.filemonitor.example;

import java.io.File;
import java.io.FileFilter;

import com.hp.it.perf.monitor.filemonitor.FileMonitorService;
import com.hp.it.perf.monitor.filemonitor.FolderContentProvider;
import com.hp.it.perf.monitor.filemonitor.LineRecord;
import com.hp.it.perf.monitor.filemonitor.UniqueFile;
import com.hp.it.perf.monitor.filemonitor.nio.MultiMonitorService;
import com.hp.it.perf.monitor.filemonitor.nio.NioFileMonitorService;

public class FileMonitorExample {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		monitorFolder(new File("."));
		monitorSingleFile(new File("sampleSingle.txt"));
	}

	private static void monitorFolder(File sampleFolder) throws Exception {
		FileMonitorService monitorService = new MultiMonitorService();
		FolderContentProvider folder = new FolderContentProvider();
		folder.setFolder(sampleFolder);
		folder.setMonitorService(monitorService);
		folder.setTailMode(true);
		folder.setFilter(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return !pathname.getName().endsWith(".jar");
			}
		});
		folder.init();
		LineRecord line;
		while ((line = folder.readLine()) != null) {
			System.out.print(new String(line.getLine(), "UTF-8"));
		}
		folder.close();
	}

	private static void monitorSingleFile(File sampleFile) throws Exception {
		FileMonitorService monitorService = new NioFileMonitorService();
		UniqueFile singleFile = new UniqueFile();
		singleFile.setFile(sampleFile);
		singleFile.setMonitorService(monitorService);
		singleFile.init();
		LineRecord line;
		while ((line = singleFile.readLine()) != null) {
			System.out.print(new String(line.getLine(), "UTF-8"));
		}
		singleFile.close();
	}
}
