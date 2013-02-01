package com.hp.it.perf.monitor.filemonitor.example;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URLConnection;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
			if (pathname.getName().matches(".+\\.log.*")) {
				return true;
			}
			String contentType = URLConnection.getFileNameMap()
					.getContentTypeFor(pathname.toString());
			return (contentType != null && (contentType.startsWith("text/") || contentType
					.endsWith("/xml")));
		}
	};

	private static Map<Long, FileContentInfo> infos = new HashMap<Long, FileContentInfo>();

	private static final Logger log = LoggerFactory
			.getLogger(FileMonitorMain.class);

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			args = new String[] { "." };
		}
		boolean monitor = false;
		UniqueFile.DefaultIdleTimeout = 600;
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
				provider.setInitOffset(-1);
				suite.getProviders().add(provider);
			}
		}
		suite.init();
		Hashtable<String, String> prop = new Hashtable<String, String>();
		prop.put("type", suite.getProviderType());
		prop.put("name", "composite1");
		ManagementFactory.getPlatformMBeanServer().registerMBean(suite,
				ObjectName.getInstance(CompositeContentProvider.DOMAIN, prop));
		// setup remote mbean server
		setupJMXConnectorServer();
		LineRecord line;
		refreshFiles(suite);
		if (monitor) {
			String lastFileName = null;
			int lastLineNo = 0;
			while ((line = suite.readLine()) != null) {
				String fileName = getFileName(line.getProviderId(), suite);
				if (!fileName.equals(lastFileName)) {
					if (lastFileName != null) {
						// end last file
						log.info("{}: {}", lastFileName, lastLineNo);
					}
					lastLineNo = 0;
					lastFileName = fileName;
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
		LocateRegistry.createRegistry(port);
		String theLocation = System.getProperty("monitor.jmx.location",
				"filemonitor");
		String serviceURL = "service:jmx:rmi:///jndi/rmi://" + theHost + ":"
				+ port + "/" + theLocation;
		Map<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.INITIAL_CONTEXT_FACTORY,
				"com.sun.jndi.rmi.registry.RegistryContextFactory");
		environment.put(RMIConnectorServer.JNDI_REBIND_ATTRIBUTE, "true");
		JMXConnectorServer connectorServer = JMXConnectorServerFactory
				.newJMXConnectorServer(new JMXServiceURL(serviceURL),
						environment, ManagementFactory.getPlatformMBeanServer());
		connectorServer.start();
		log.info("==> Target JMX Service URL is {}", serviceURL);
	}

	private static void refreshFiles(FileContentProvider suite)
			throws IOException {
		for (FileContentInfo content : suite.getFileContentInfos(false, true)) {
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
		if (info == null || info.getCurrentFileName() == null) {
			return "[UNKOWN FILE]";
		} else {
			return info.getCurrentFileName();
		}
	}

}
