package org.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

import java.io.File;

@SpringBootApplication(exclude= {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class SimApplication {
	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir
						(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		if(dir.delete()) {
			return true;
		} else {
			return false;
		}
	}
	public static void main(String[] args) {
		String path = System.getProperty("user.dir")+"\\OutputFiles\\yaml";
		deleteDir(new File(path));
		File dir = new File(path);
		dir.mkdirs();
		path = System.getProperty("user.dir")+"\\OutputFiles\\jobResult";
		dir = new File(path);
		dir.mkdirs();
		path = System.getProperty("user.dir")+"\\OutputFiles\\hostUtil";
		dir = new File(path);
		dir.mkdirs();
		path = System.getProperty("user.dir")+"\\OutputFiles\\bandwidthUtil";
		dir = new File(path);
		dir.mkdirs();
		path = System.getProperty("user.dir")+"\\OutputFiles\\latency";
		dir = new File(path);
		dir.mkdirs();
		path = System.getProperty("user.dir")+"\\OutputFiles\\faultLog";
		dir = new File(path);
		dir.mkdirs();
		System.out.println("后端已启动");
		System.setProperty("java.awt.headless","false");
		SpringApplication.run(SimApplication.class, args);

	}

}
