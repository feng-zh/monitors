package com.hp.it.perf.monitor.files;

import java.net.URL;

public interface FileClusterStrategy {

	String getClusterName(String baseName, URL url);

}
