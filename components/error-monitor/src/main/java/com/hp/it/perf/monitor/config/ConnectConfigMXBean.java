package com.hp.it.perf.monitor.config;

import java.util.Map;

public interface ConnectConfigMXBean {

	public abstract Map<String, String> getConfigs();

	public abstract void setConfigs(Map<String, String> configs);

	public abstract void put(String k, String v);

	public abstract void remove(String k);

}