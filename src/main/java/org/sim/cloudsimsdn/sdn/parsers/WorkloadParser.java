/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.sim.cloudsimsdn.sdn.parsers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.sim.cloudsimsdn.Cloudlet;
import org.sim.cloudsimsdn.Datacenter;
import org.sim.cloudsimsdn.UtilizationModel;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.Configuration;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNDatacenter;
import org.sim.cloudsimsdn.sdn.workload.*;
import org.sim.controller.AssignInfo;
import org.sim.service.Constants;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.sim.cloudsimsdn.core.CloudSim.assignInfoMap;
import static org.sim.cloudsimsdn.core.CloudSim.getEntityId;
import static org.sim.controller.SDNController.*;

public class WorkloadParser {
	private static final int NUM_PARSE_EACHTIME = 200;

	private double forcedStartTime = -1;
	private double forcedFinishTime = Double.POSITIVE_INFINITY;

	private final Map<String, Integer> vmNames;
	private final Map<String, Integer> flowNames;
	private String file;
	private int userId;
	private UtilizationModel utilizationModel;

	private List<Workload> parsedWorkloads;

	private WorkloadResultWriter resultWriter = null;

	private int workloadNum = 0;

	private BufferedReader bufReader = null;

	public WorkloadParser(String file, int userId, UtilizationModel cloudletUtilModel,
			Map<String, Integer> vmNameIdMap, Map<String, Integer> flowNameIdMap) {
		this.file = file;
		this.userId = userId;
		this.utilizationModel = cloudletUtilModel;
		this.vmNames = vmNameIdMap;
		this.flowNames = flowNameIdMap;

		String result_file = getResultFileName(this.file);
		resultWriter = new WorkloadResultWriter(result_file);
//		openFile();
	}

	public void forceStartTime(double forcedStartTime) {
		this.forcedStartTime = forcedStartTime;
	}

	public void forceFinishTime(double forcedFinishTime) {
		this.forcedFinishTime = forcedFinishTime;
	}

	public static String getResultFileName(String fileName) {
		String result_file = workload_result;
		return result_file;
	}

	/**
	 * parse 消息
	 */
	public void parseNextWorkloads() {
		this.parsedWorkloads = new ArrayList<Workload>();
		parseNext(NUM_PARSE_EACHTIME); /* 创建消息的核心函数 */
	}

	public List<Workload> getParsedWorkloads() {
		return this.parsedWorkloads;
	}


	public WorkloadResultWriter getResultWriter() {
		return resultWriter;
	}


	private int getVmId(String vmName) {
		Integer vmId = this.vmNames.get(vmName);
		if(vmId == null) {
			System.err.println("Cannot find VM name:"+vmName);
			return -1;
		}
		return vmId;
	}

	private Cloudlet generateCloudlet(long cloudletId, int vmId, int length) {
		int peNum=1;
		long fileSize = 300;
		long outputSize = 300;
		Cloudlet cloudlet= new Cloudlet((int)cloudletId, length, peNum, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		cloudlet.setUserId(userId);
		cloudlet.setVmId(vmId);

		return cloudlet;
	}


	/**
	 * 从容器模块读取消息
	 * 将所有的消息制作成“event”放到全局仿真队列中
	 */
	private void parseNext(int numRequests) { //参数不使用
		try{
			// Constants.workloads是容器模块赋值的静态变量，包含所有消息
			for(Workload wl: Constants.workloads){
				wl.resultWriter = this.resultWriter;
				//time消息起始时间
				// contractRate将时间从微秒转为秒，Cloudsim仿真时间以秒为单位
				wl.time = wl.time * contractRate;
				//发送者，Vm即代表容器
				wl.submitVmId = getVmId(wl.submitVmName);
				//接受者
				wl.destVmId = getVmId(wl.destVmName);
				//消息大小
				// 8代表Byte到bit的转化，0.001代表数值放缩
				// 在Cloudsim系统中，所有与数据量大小相关的值（带宽和包大小）都缩小了1000倍，提升效率
				// 比如10M带宽从10*1000000变为10*1000; 1000B网络包从1000变为1。
				// 相除后传输时间和原本是一样的
				wl.submitPktSize = wl.submitPktSize * 0.008;

				//将消息制作成Cloudsim网络仿真需要的格式
				// --------- cloudsim自带的格式，可忽略 -----------------
				Request req = new Request(userId);
				req.addActivity(
						new Processing(
								generateCloudlet(req.getRequestId(), wl.submitVmId, 0)
						)
				);
				Request endreq = new Request(userId);
				endreq.addActivity(
						new Processing(
								generateCloudlet(req.getRequestId(), wl.destVmId, 0)
						)
				);
				req.addActivity(new Transmission(wl.submitVmId, wl.destVmId, wl.submitPktSize, this.flowNames.get("default"), endreq));
				wl.request = req;
				// -----------------------------------------------------
				parsedWorkloads.add(wl);
			}

		}catch (Exception e){
			e.printStackTrace();
		}

		return;

	}

	public int getWorkloadNum() {
		return workloadNum;
	}

	public int getGroupId() {
		String first_word = this.file.split("_")[0];
		int groupId = 0;
		try {
			groupId = Integer.parseInt(first_word);
		} catch (NumberFormatException e) {
			// Do nothing
		}
		return groupId;
	}
}
