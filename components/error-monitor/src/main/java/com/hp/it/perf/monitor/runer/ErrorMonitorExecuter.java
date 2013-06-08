package com.hp.it.perf.monitor.runer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.JMException;
import javax.management.remote.JMXServiceURL;

import com.hp.it.perf.monitor.config.ConnectConfigEnum;
import com.hp.it.perf.monitor.config.ConnectConfigMXBean;
import com.hp.it.perf.monitor.config.ErrorMonitorConfigMXBean;
import com.hp.it.perf.monitor.errors.ErrorMonitorMain;

public class ErrorMonitorExecuter extends Thread {

	private Map<String, ErrorMonitorConfigMXBean> configs;
	private ConnectConfigMXBean conConfig;
	
	public ErrorMonitorExecuter(Map<String, ErrorMonitorConfigMXBean> c, ConnectConfigMXBean con){
		this.configs = c;
		this.conConfig = con;
	}
	
	@Override
	public void run() {
		try {
			new ErrorMonitorMain(configs)
			.monitor(new JMXServiceURL(conConfig.getConfigs().get(ConnectConfigEnum.SERVICEURL.toString())));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JMException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
