package com.hp.it.perf.monitor.files;

import java.util.List;
import java.util.Map;

public interface FileSet extends FileInstanceChangeAware {

	public List<? extends FileInstance> listInstances();

	public Map<String, ? extends FileCluster> listClusters();

}
