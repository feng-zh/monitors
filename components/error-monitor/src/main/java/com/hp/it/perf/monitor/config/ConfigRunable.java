package com.hp.it.perf.monitor.config;

import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanServer; 
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class ConfigRunable {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{
		List<String> in = new ArrayList<String>(1);
		in.add("error");
		List<String> out = new ArrayList<String>(1);
		out.add("sp4tsdiag");
		
		ErrorMonitorConfigMXBean content = new ErrorMonitorConfig(in, null, false);
		ErrorMonitorConfigMXBean filename = new ErrorMonitorConfig(null, out, true);
		
		ConnectConfigMXBean conConfig = new ConnectConfig();
		conConfig.put(ConnectConfigEnum.SERVICEURL.toString(), "service:jmx:rmi:///jndi/rmi://d6t0009g.atlanta.hp.com:12099/filemonitor");

		MBeanServer mbs = 
	            ManagementFactory.getPlatformMBeanServer(); 
	                 
		ObjectName contentBeanName = new ObjectName("com.hp.it.perf.monitor.config:type=ContentErrorMonitorConfig");
		ObjectName fileNameBeanName = new ObjectName("com.hp.it.perf.monitor.config:type=FileNameErrorMonitorConfig");
		ObjectName connectConfigBeanName = new ObjectName("com.hp.it.perf.monitor.config:type=ConnectionConfig");

		mbs.registerMBean(content, contentBeanName);
		mbs.registerMBean(filename, fileNameBeanName);
		mbs.registerMBean(conConfig, connectConfigBeanName);
		
		System.out.println("Waiting..."); 
        
		while(true){
			Thread.sleep(5000);
			System.out.println("content include: " + content.getIncludes());
			System.out.println("filename exclude: " + filename.getExcludes());
		}
	}

}
