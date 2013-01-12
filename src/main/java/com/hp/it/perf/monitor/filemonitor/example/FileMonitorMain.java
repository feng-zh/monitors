package com.hp.it.perf.monitor.filemonitor.example;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import com.hp.it.perf.monitor.filemonitor.CompositeContentProvider;
import com.hp.it.perf.monitor.filemonitor.FileContentInfo;
import com.hp.it.perf.monitor.filemonitor.FileContentProvider;
import com.hp.it.perf.monitor.filemonitor.FileMonitorService;
import com.hp.it.perf.monitor.filemonitor.FolderContentProvider;
import com.hp.it.perf.monitor.filemonitor.LineRecord;
import com.hp.it.perf.monitor.filemonitor.UniqueFile;
import com.hp.it.perf.monitor.filemonitor.nio.MultiMonitorService;

public class FileMonitorMain {

	private static FileFilter textFilter = new FileFilter() {

		@Override
		public boolean accept(File pathname) {
			if (pathname.getName().endsWith(".log")) {
				return true;
			}
			String contentType = URLConnection.getFileNameMap()
					.getContentTypeFor(pathname.toString());
			return (contentType != null && (contentType.startsWith("text/") || contentType
					.endsWith("/xml")));
		}
	};

	private static Map<Long, FileContentInfo> infos = new HashMap<Long, FileContentInfo>();

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			args = new String[] { "." };
		}
		boolean monitor = false;
		UniqueFile.DefaultIdleTimeout = 60;
		UniqueFile.DefaultLazyOpen = true;
		FileMonitorService monitorService = new MultiMonitorService();
		CompositeContentProvider suite = new CompositeContentProvider();
		for (int i = 0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-monitor")) {
				monitor = true;
			}
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
		ManagementFactory.getPlatformMBeanServer().registerMBean(
				suite,
				ObjectName.getInstance(CompositeContentProvider.DOMAIN, "name",
						"compositeProvider"));
		// setup remote mbean server
		setupJMXConnectorServer();
		LineRecord line;
		refreshFiles(suite);
		if (monitor) {
			String lastFileName = null;
			int lastLineNo = 0;
			while ((line = suite.readLine()) != null) {
				String fileName = getFileName(line.getProviderId(), suite);
				if (fileName.equals(lastFileName)) {
					System.out.print(".");
					if (lastLineNo % 10 == 0) {
						System.out.flush();
					}
				} else {
					if (lastFileName != null) {
						// end last file
						System.out.println("(" + lastLineNo + ")");
					}
					lastLineNo = 0;
					lastFileName = fileName;
					System.out.print("Monitor on " + fileName + ": .");
				}
				lastLineNo++;
			}
		} else {
			while ((line = suite.readLine()) != null) {
				System.out.print(getFileName(line.getProviderId(), suite) + ":"
						+ new String(line.getLine(), "UTF-8"));
			}
		}
		suite.close();
	}

	private static void setupJMXConnectorServer() throws IOException {
		String theHost = InetAddress.getLocalHost().getHostName();
		int port = Integer.getInteger("monitor.rmi.port", 12099);
		try {
			Registry registry = LocateRegistry.getRegistry(port);
		} catch (RemoteException e) {
			LocateRegistry.createRegistry(port);
		}
		String theLocation = System.getProperty("monitor.jmx.location",
				"filemonitor");
		String serviceURL = "service:jmx:rmi://" + theHost + ":" + port
				+ "/jndi/rmi://" + theHost + ":" + port + "/" + theLocation;
		System.out.printf("Target JMX Service URL is %s%n", serviceURL);
		Map<String, String> environment = new Hashtable<String, String>();
		JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL(
				serviceURL), environment, ManagementFactory
				.getPlatformMBeanServer());
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
