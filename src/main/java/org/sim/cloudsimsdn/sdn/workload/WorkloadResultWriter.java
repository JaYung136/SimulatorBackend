/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.workload;

import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.Configuration;
import org.sim.cloudsimsdn.sdn.LogWriter;
import org.sim.cloudsimsdn.sdn.main.LogPrinter;
import org.sim.controller.AssignInfo;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadResultWriter {
	private boolean headPrinted=false;
	private String filename;
	private LogWriter out = null;

	// For statistics
	private double totalServeTime;	// All serve time
	private double cpuServeTime; // CPU only
	private double networkServeTime; // Network only
	private double maxPerTime = 0.0;

	private int printedWorkloadNum; 	// Number of workloads
	private int overNum;	// Number of workloads exceeds estimated response time
	private int timeoutNum = 0;

	private int cloudletNum;	// Number of Cloudlets
	private int cloudletOverNum;	// Number of Cloudlets exceeds estimated finish time
	private int transmissionNum;	// Number of transmissions
	private int transmissionOverNum;	// Number of transmissions exceeds estimated transmission time
	private DecimalFormat df = new DecimalFormat();

	//private PriorityQueue<Workload> workloadToPrint;
	//private int nextId = 0;

	//WorkloadResultWriterThread thread;

	public WorkloadResultWriter(String file) {
		df.setMaximumFractionDigits(8);
		df.setGroupingUsed(false);

		this.filename = file;

		LogWriter.resetLogger(this.filename);
		out = LogWriter.getLogger(filename);

		//workloadToPrint = new PriorityQueue<Workload>();
		//thread = new WorkloadResultWriterThread(this);
		//new Thread(thread).start();
	}

	public void writeResult(Workload wl) {
		printWorkloadBuffer(wl);
		//printWorkload(wl);
		//threadPrintWorkload(wl);
	}

	// Multi-thread Version
	/*
	private void threadPrintWorkload(Workload wl) {
		thread.enqueue(wl);
	}

	private void threadExit() {
		thread.setExit();
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	*/

	private static final int workloadBufferSize = 1000;
	private List<Workload> workloadBuffer = new ArrayList<Workload>(workloadBufferSize);

	private void printWorkloadBuffer(Workload wl) {
		workloadBuffer.add(wl);
		if(workloadBuffer.size() >= workloadBufferSize)
			flushWorkloadBuffer();
	}

	private List<Workload> flushWorkloadBuffer() {
		List<Workload> oldbuffer = workloadBuffer;
		List<Workload> res = new ArrayList<>();
		for(Workload wl:workloadBuffer) {
			Workload wlupdated = printWorkload(wl);
			res.add(wlupdated);
		}
		workloadBuffer = new ArrayList<Workload>(workloadBufferSize);
		return res;
	}


//
//	private void flushWorkloads() {
//		Workload wl;
//		while((wl = workloadToPrint.peek()) != null && wl.workloadId == nextId) {
//			wl = workloadToPrint.poll();
//			printWorkload(wl);
//			nextId++;
//		}
//	}
//
	public Workload printWorkload(Workload wl) {//TODO:打印单条workload
		if(!headPrinted) {
			this.printHead(wl);
			headPrinted = true;
		}

		double serveTime;

		printDetailInt(wl.workloadId);
		printDetail(" " + wl.msgName + ",");
		printDetail(" " + wl.submitVmName + ",");
		printDetail(" " + wl.destVmName + ",");
		printDetailInt(wl.appId);
		printDetailFloat(wl.time);

		if(wl.failed) {
			printRequest(wl.request, false);

			printDetail("TimeOut");
			printDetail("\n");

			this.timeoutNum++;
		}
		else {
			printRequest(wl.request, true);

//			BigDecimal temServeTime = new BigDecimal(getWorkloadFinishTime(wl)).subtract(new BigDecimal(getWorkloadStartTime(wl)));

			// TODO:Workload类新建 DAG调度时间 和 总端到端时间，在此计算
			serveTime= getWorkloadFinishTime(wl) - getWorkloadStartTime(wl);//temServeTime.doubleValue();
			wl.networkfinishtime = getWorkloadFinishTime(wl);
			AssignInfo destinfo = CloudSim.assignInfoMap.get(wl.destVmName);
			Double loopstart = destinfo.starttime;
			Double loopend = destinfo.endtime;
			Double ps = destinfo.pausestart;
			Double pe = destinfo.pauseend;
			/**
			 * 以周期为循环，不断迭代找到最合适的end2end接收时间
			 * net:网络传输到达时刻
			 * 一次循环中：
			 * if net>=下一循环start
			 * 		到下一循环
			 * if net>  循环end && net<下一循环start
			 *		wl.end2endfinishtime = 下一循环start;
			 * if net<=循环end && net>=循环start+ps+pe
			 * 		wl.end2endfinishtime = net;
			 * if net<循环start+ps+pe && net>循环start+ps
			 * 		wl.end2endfinishtime = 循环start+ps+pe;
			 * if net<=循环start+ps && net>=循环start
			 * 		wl.end2endfinishtime = net;
			 * if net<循环start
			 * 		wl.end2endfinishtime = 循环start;
			 */
			while (true){
//				System.out.println("networkfinishtime:"+wl.networkfinishtime+" loopstart:"+loopstart+" loopend:"+loopend);
				if(wl.networkfinishtime >= loopstart + destinfo.containerperiod){
					loopstart += destinfo.containerperiod;
					loopend += destinfo.containerperiod;
					continue;
				}
				if(wl.networkfinishtime > loopend && wl.networkfinishtime < loopstart + destinfo.containerperiod){
					wl.end2endfinishtime = loopstart + destinfo.containerperiod;
					break;
				}
				if(wl.networkfinishtime <= loopend && wl.networkfinishtime >= loopstart+ps+pe){
					wl.end2endfinishtime = wl.networkfinishtime;
					break;
				}
				if(wl.networkfinishtime<loopstart+ps+pe && wl.networkfinishtime>loopstart+ps){
					wl.end2endfinishtime = loopstart+ps+pe;
					break;
				}
				if(wl.networkfinishtime<=loopstart+ps && wl.networkfinishtime>=loopstart){
					wl.end2endfinishtime = wl.networkfinishtime;
					break;
				}
				if (wl.networkfinishtime<loopstart) {
					wl.end2endfinishtime = loopstart;
					break;
				}
			}
			// TODO:消息时延增加随机化
			wl.networktransmissiontime = (wl.networkfinishtime - wl.time) * (0.95 + Math.random()/10.0);
			wl.dagschedulingtime = wl.end2endfinishtime - wl.time - wl.networktransmissiontime;
			if(wl.dagschedulingtime < 0.0){
				wl.networktransmissiontime = wl.networkfinishtime - wl.time;
				wl.dagschedulingtime = wl.end2endfinishtime - wl.networkfinishtime;
			}

			maxPerTime = (maxPerTime > serveTime)? maxPerTime : serveTime;
			printDetail(String.format(LogPrinter.fFloat, serveTime));
			printDetail("\n");

			this.totalServeTime += serveTime;

			if(isOverTime(wl, serveTime)) {
				overNum++;
			}
			printedWorkloadNum++;
		}
		return wl;
	}


	private void printRequestTitle(Request req) {
		List<Activity> acts = req.getRemovedActivities();
		for(Activity act:acts) {
			if(act instanceof Transmission) {
				Transmission tr=(Transmission)act;

				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					print(String.format(LogPrinter.fString, "Tr:StartTime"));
					print(String.format(LogPrinter.fString, "Tr:EndTime"));
				}

				print(String.format(LogPrinter.fString, "Tr:NetworkTime"));

				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					print(String.format(LogPrinter.fString, "Tr:Size"));
					print(String.format(LogPrinter.fString, "Tr:Channel"));
				}

				printRequestTitle(tr.getPacket().getPayload());
			}
			else {
				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					print(String.format(LogPrinter.fString, "Pr:StartTime"));
					print(String.format(LogPrinter.fString, "Pr:EndTime"));
				}

				print(String.format(LogPrinter.fString, "Pr:CPUTime"));

				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					print(String.format(LogPrinter.fString, "Pr:Size"));
				}

			}
		}
	}

	private void printRequest(Request req, boolean includeStatistics) {

		List<Activity> acts = req.getRemovedActivities();
		for(Activity act:acts) {
			if(act instanceof Transmission) {
				Transmission tr=(Transmission)act;
				double serveTime = tr.getServeTime();

				if(includeStatistics) {
					networkServeTime += serveTime;
					transmissionNum++;

					if(isOverTime(tr))
						transmissionOverNum++;
				}

				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					printDetailFloat(tr.getStartTime());	 // trans Start time
					printDetailFloat(tr.getFinishTime());	 // trans Finish time
				}

				printDetailFloat(serveTime); // Network processing time

				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					printDetailFloat(tr.getPacket().getSize()); // Size
					printDetailInt(tr.getPacket().getFlowId()); // Channel #
				}
				printRequest(tr.getPacket().getPayload(), includeStatistics);
			}
			else {
				Processing pr=(Processing)act;
				double serveTime = pr.getServeTime();

				if(includeStatistics) {
					cpuServeTime += serveTime;
					cloudletNum++;
					if(isOverTime(pr))
						cloudletOverNum++;
				}

				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					//printDetailFloat(pr.getCloudlet().getSubmissionTime());	// Start time
					printDetailFloat(pr.getStartTime());	// Start time
					printDetailFloat(pr.getFinishTime());	// Start time
				}
				printDetailFloat(serveTime); // Processing time
				if(Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
					//printDetailInt(pr.getCloudlet().getCloudletLength()); // Size
					printDetailInt(pr.cloudletTotalLength); // Size
				}
			}
		}
	}

	private void printHead(Workload sample) {
		print(String.format(LogPrinter.fString, "Workload_ID"));
		print(String.format(LogPrinter.fString, "MessageName"));
		print(String.format(LogPrinter.fString, "Src_App"));
		print(String.format(LogPrinter.fString, "Dst_App"));
		print(String.format(LogPrinter.fString, "App_ID"));
		print(String.format(LogPrinter.fString, "SubmitTime"));
		printRequestTitle(sample.request);
		print(String.format(LogPrinter.fString, "ResponseTime"));
		printLine();
	}

//	public void printWorkloadList(List<Workload> wls) {
//		for(Workload wl:wls) {
//			printWorkload(wl);
//		}
//	}

	public List<Workload> printStatistics() {
		//threadExit();
		List<Workload> wls = flushWorkloadBuffer(); //打印表格
//		printLine("#======================================");
//		printLine("#Number of workloads:" + printedWorkloadNum);
//		printLine("#Timeout workloads:" + timeoutNum);
//		if(timeoutNum + printedWorkloadNum != 0)
//			printLine("#Timeout workloads per cent:" + timeoutNum / (timeoutNum + printedWorkloadNum));
//
//		printLine("#Over workloads:" + overNum);
//		if(printedWorkloadNum != 0)
//			printLine("#Over workloads per cent:" + overNum / printedWorkloadNum);
//		printLine("#Number of Cloudlets:" + cloudletNum);
//		printLine("#Over Cloudlets:" + cloudletOverNum);
//		if(cloudletNum != 0)
//			printLine("#Over Cloudlets per cent:" + cloudletOverNum / cloudletNum);
//		printLine("#Number of transmissions:" + transmissionNum);
//		printLine("#Over transmissions:" + transmissionOverNum);
//		if(transmissionNum != 0)
//			printLine("#Over transmissions per cent:" + transmissionOverNum / transmissionNum);
//		printLine("#======================================");
//		printLine("#Total serve time:" + totalServeTime);
//		printLine("#CPU serve time:" + cpuServeTime);
//		printLine("#Network serve time:" + networkServeTime);
//		if(printedWorkloadNum != 0)
//		{
//			printLine("#Average total serve time:" + totalServeTime/printedWorkloadNum);
//			printLine("#Average CPU serve time per workload:" + cpuServeTime/printedWorkloadNum);
//			printLine("#Average network serve time per workload:" + networkServeTime/printedWorkloadNum);
//		}
//		if(cloudletNum != 0)
//			printLine("#Average CPU serve time per Cloudlet:" + cpuServeTime/cloudletNum);
//		if(transmissionNum != 0)
//			printLine("#Average network serve time per transmission:" + networkServeTime/transmissionNum);
		return wls;
	}

	public int getWorklaodNum() {
		return printedWorkloadNum;
	}
	public int getTimeoutNum() {
		return timeoutNum;
	}
	public int getWorklaodNumOvertime() {
		return overNum;
	}
	public int getWorklaodNumCPU() {
		return cloudletNum;
	}
	public int getWorklaodNumCPUOvertime() {
		return cloudletOverNum;
	}
	public int getWorklaodNumNetwork() {
		return transmissionNum;
	}
	public int getWorklaodNumNetworkOvertime() {
		return transmissionOverNum;
	}
	public double getServeTime() {
		return totalServeTime;
	}
	public double getMaxPerTime() {return maxPerTime;}
	public double getServeTimeCPU() {
		return cpuServeTime;
	}
	public double getServeTimeNetwork() {
		return networkServeTime;
	}

	public static double getWorkloadStartTime(Workload w) {
		List<Activity> acts = getAllActivities(w.request);


		for(Activity act:acts) {
//			if(act instanceof Processing) {
			//TODO:workload的起始时间从transmisson开始算起
			if(act instanceof Transmission) {
				return act.getStartTime();
			}
		}
		return -1;
	}

	public static double getWorkloadFinishTime(Workload w) {
		double finishTime = -1;

		if(w.failed)
			return finishTime;

		List<Activity> acts = getAllActivities(w.request);

		for(Activity act:acts) {
			if(act instanceof Processing) {
				finishTime = act.getFinishTime();
				//Processing pr=(Processing)act;
				//finishTime=pr.getCloudlet().getFinishTime();
			}
		}
		return finishTime;
	}

	private boolean isOverTime(final Activity ac) {
		double serveTime = ac.getServeTime();
		double expectedTime = getExpectedTime(ac);

		if(serveTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR ) {
			// SLA viloated. Served too late.
			return true;
		}
		return false;
	}

	private boolean isOverTime(final Workload wl, double serveTime) {

		if(Configuration.DEBUG_CHECK_OVER_TIME_REQUESTS) {
			double expectedTime = 0;
			List<Activity> acts = getAllActivities(wl.request);

			for(Activity ac:acts) {
				expectedTime += getExpectedTime(ac);
			}

			if(serveTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR ) {
				// SLA viloated. Served too late.
				return true;
			}
			return false;
		}

		return false;
	}

	private double getExpectedTime(Activity ac) {
		double expectedTime = ac.getExpectedTime();

		if(ac instanceof Transmission) {
			if(expectedTime < CloudSim.getMinTimeBetweenEvents())
				expectedTime = CloudSim.getMinTimeBetweenEvents();
		}
		else {
			if(expectedTime < CloudSim.getMinTimeBetweenEvents())
				expectedTime = CloudSim.getMinTimeBetweenEvents();
		}
		return expectedTime;
	}


	protected void printLine() {
		out.printLine();
	}

	protected void print(String s) {
		out.print(s);
	}

	protected void printLine(String s) {
		out.printLine(s);
	}

	protected void printDetail(String s) {
		if(Configuration.DEBUG_RESULT_WRITE_DETAIL)
			out.print(s);
	}

	protected void printDetailInt(long l) {
		//printDetail(String.format(LogPrinter.fInt, l));
		printDetail(" "+l+","); // "%"+tabSize+"d"+indent;
	}

	protected void printDetailFloat(double f) {
		//printDetail(String.format(LogPrinter.fFloat, f));
		printDetail(" "+df.format(f)+","); // "%"+tabSize+".3f"+indent;
	}

	private static List<Activity> getAllActivities(Request req) {
		List<Activity> outputActList = new ArrayList<Activity>();
		getAllActivities(req, outputActList);
		return outputActList;
	}
	private static void getAllActivities(Request req, List<Activity> outputActList) {
		List<Activity> acts = req.getRemovedActivities();
		for(Activity act:acts) {
			outputActList.add(act);
			if(act instanceof Transmission) {
				getAllActivities(((Transmission)act).getPacket().getPayload(), outputActList);
			}
		}
	}
}
