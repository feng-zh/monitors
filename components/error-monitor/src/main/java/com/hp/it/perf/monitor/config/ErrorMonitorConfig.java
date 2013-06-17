package com.hp.it.perf.monitor.config;

import java.util.ArrayList;
import java.util.List;

public class ErrorMonitorConfig implements ErrorMonitorConfigMXBean {

	private List<String> excludes = new ArrayList<String>(5);
	private boolean greenRoad;
	
	private List<String> includes = new ArrayList<String>(5);
	
	public ErrorMonitorConfig(){}
	public ErrorMonitorConfig(List<String> includes, List<String> excludes, boolean green){
		this.includes = includes;
		this.excludes = excludes;
		this.greenRoad = green;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#getExcludes()
	 */
	@Override
	public List<String> getExcludes() {
		return excludes;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#getIncludes()
	 */
	@Override
	public List<String> getIncludes() {
		return includes;
	}
	
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#isChecked(java.lang.String)
	 */
	@Override
	public boolean isChecked(String s){
		boolean result = false;
		
		if(s == null || s.trim().length() <= 0){
			return result;
		}
		
		if(greenRoad){
			result = true;
		}else{
			if(includes != null && !includes.isEmpty()){
				for(String in: includes){
					if(s.contains(in)){
						result = true;
						break;
					}
				}
			}else{
				result = true;
			}
			
			if(result && excludes != null && !excludes.isEmpty()){
				for(String out: excludes){
					if(s.contains(out)){
						result = false;
						break;
					}
				}
			}
		}
		
		return result;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#isGreenRoad()
	 */
	@Override
	public boolean isGreenRoad() {
		return greenRoad;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#setExcludes(java.util.List)
	 */
	@Override
	public void setExcludes(List<String> excludes) {
		this.excludes = excludes;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#setGreenRoad(boolean)
	 */
	@Override
	public void setGreenRoad(boolean greenRoad) {
		this.greenRoad = greenRoad;
	}
	
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#setIncludes(java.util.List)
	 */
	@Override
	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#addInclude(java.lang.String)
	 */
	@Override
	public void addInclude(String n){
		this.includes.add(n);
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#addExclude(java.lang.String)
	 */
	@Override
	public void addExclude(String n){
		this.excludes.add(n);
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#removeInclude(java.lang.String)
	 */
	@Override
	public void removeInclude(String n){
		if(this.includes == null || this.includes.isEmpty()){
			return;
		}
		synchronized(this.includes){
			for(String s: includes){
				if(s.equalsIgnoreCase(n)){
					includes.remove(s);
					break;
				}
			}
		}
	}
	/* (non-Javadoc)
	 * @see com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean#removeExclude(java.lang.String)
	 */
	@Override
	public void removeExclude(String n){
		if(this.excludes == null || this.excludes.isEmpty()){
			return;
		}
		synchronized(this.excludes){
			for(String s: excludes){
				if(s.equalsIgnoreCase(n)){
					excludes.remove(s);
					break;
				}
			}
		}
	}
}
