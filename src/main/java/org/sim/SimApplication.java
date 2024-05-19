package org.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

@SpringBootApplication(exclude= {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class SimApplication {
	/* 删除旧的输出结果目录和文件 */
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

	private static void errorLog() {
		try {
			// 创建一个文件输出流，指向错误日志文件
			String path = System.getProperty("user.dir")+"\\OutputFiles\\error.txt";
			File file = new File(path);
			FileOutputStream fos = new FileOutputStream(file);

			// 将标准错误流重定向到文件输出流
			System.setErr(new PrintStream(fos));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		String path = System.getProperty("user.dir")+"\\OutputFiles\\yaml";
		deleteDir(new File(path));
		/* 新建输出结果目录 */
		File dir = new File(path);
		dir.mkdirs();
		path = System.getProperty("user.dir")+"\\Intermediate";
		dir = new File(path);
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

		errorLog();
		System.out.println("后端已启动");
		System.setProperty("java.awt.headless","false");
		SpringApplication.run(SimApplication.class, args);

	}

}
