package com.hp.it.perf.monitor.filemonitor.example;

import java.io.File;

import com.hp.it.perf.monitor.filemonitor.FileMonitorService;
import com.hp.it.perf.monitor.filemonitor.LineRecord;
import com.hp.it.perf.monitor.filemonitor.NioFileMonitorService;
import com.hp.it.perf.monitor.filemonitor.UniqueFile;

public class FileMonitorExample {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		FileMonitorService monitorService = new NioFileMonitorService();
		UniqueFile singleFile = new UniqueFile();
		singleFile.setFile(new File("sampleSingle.txt"));
		singleFile.setMonitorService(monitorService);
		singleFile.init();
		LineRecord line;
		while ((line = singleFile.readLine()) != null) {
			System.out.print(new String(line.getLine(), "UTF-8"));
		}
		singleFile.close();
	}
}
