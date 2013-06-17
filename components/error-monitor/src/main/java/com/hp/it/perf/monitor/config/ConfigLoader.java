package com.hp.it.perf.monitor.config;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigLoader {

	Properties properties = null;
	
	public ConfigLoader(String url){
		properties = new Properties();
		InputStream in = null;
		
		if(url != null){	
			try {
				in = new BufferedInputStream(new FileInputStream(new File(url)));
				properties.load(in);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally{
				if(in != null){
					try {
						in.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		if(properties.isEmpty()){
			try {
				
				in = ClassLoader.getSystemResourceAsStream("errormonitor.properties");
				properties.load(in);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally{
				if(in != null){
					try {
						in.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	public List<String> getProperties(String k){
	
			List<String> result = new ArrayList<String>(5);
			
			if(k != null && !this.properties.isEmpty()){
				String pro = this.properties.getProperty(k);
				if(pro != null){
					String[] strings = pro.split(",");
					for(String s: strings){
						result.add(s);
					}
				}
			}
			
			return result;
	}
}
