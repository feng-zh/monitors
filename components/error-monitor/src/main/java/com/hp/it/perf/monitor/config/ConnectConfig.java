package com.hp.it.perf.monitor.config;

import java.util.HashMap;
import java.util.Map;

public class ConnectConfig implements ConnectConfigMXBean {

	private Map<String, String> configs = new HashMap<String, String>(1);
	
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ConnectConfigMXBean#getConfigs()
	 */
	@Override
	public Map<String, String> getConfigs() {
		return configs;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ConnectConfigMXBean#setConfigs(java.util.Map)
	 */
	@Override
	public void setConfigs(Map<String, String> configs) {
		this.configs = configs;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ConnectConfigMXBean#put(java.lang.String, java.lang.String)
	 */
	@Override
	public void put(String k, String v){
		configs.put(k, v);
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ConnectConfigMXBean#remove(java.lang.String)
	 */
	@Override
	public void remove(String k){
		configs.remove(k);
	}
}
