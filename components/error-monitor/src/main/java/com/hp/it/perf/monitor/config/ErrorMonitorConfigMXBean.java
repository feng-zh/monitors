package com.hp.it.perf.monitor.config;

import java.util.List;

public interface ErrorMonitorConfigMXBean {

	public List<String> getExcludes();

	public List<String> getIncludes();

	public boolean isChecked(String s);

	public boolean isGreenRoad();

	public void setExcludes(List<String> excludes);

	public void setGreenRoad(boolean greenRoad);

	public void setIncludes(List<String> includes);

	public void addInclude(String n);

	public void addExclude(String n);

	public void removeInclude(String n);

	public void removeExclude(String n);

}