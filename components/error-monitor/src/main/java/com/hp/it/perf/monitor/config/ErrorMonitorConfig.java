package com.hp.it.perf.monitor.config;

import java.util.ArrayList;
import java.util.List;

public class ErrorMonitorConfig {

	private List<String> excludes = new ArrayList<String>(5);
	private boolean greenRoad;
	
	private List<String> includes = new ArrayList<String>(5);
	
	public ErrorMonitorConfig(){}
	public ErrorMonitorConfig(List<String> includes, List<String> excludes, boolean green){
		this.includes = includes;
		this.excludes = excludes;
		this.greenRoad = green;
	}
	public List<String> getExcludes() {
		return excludes;
	}
	public List<String> getIncludes() {
		return includes;
	}
	
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
					if(s.toLowerCase().contains(in)){
						result = true;
						break;
					}
				}
			}
		}
		
		if(result && excludes != null && !excludes.isEmpty()){
			for(String out: excludes){
				if(s.toLowerCase().contains(out)){
					result = false;
					break;
				}
			}
		}
		
		return result;
	}
	public boolean isGreenRoad() {
		return greenRoad;
	}
	public void setExcludes(List<String> excludes) {
		this.excludes = excludes;
	}
	public void setGreenRoad(boolean greenRoad) {
		this.greenRoad = greenRoad;
	}
	
	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}
}
