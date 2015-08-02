package com.hp.it.perf.monitor.files;

import java.util.List;

public interface FileCluster extends FileInstanceChangeAware {

	public String getName();

	public List<FileInstance> listInstances();

}
