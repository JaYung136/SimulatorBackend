/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.sim.cloudsimsdn.sdn.workload;

/**
 * Class to keep workload information parsed from files.
 * This class is used in WorkloadParser
 *
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class  Workload implements Comparable<Workload> {
	public int workloadId;
	public int jobId;
	public int appId;
	public double time; //开始时刻
	public double networkfinishtime; //网络传输结束时刻
	public double switchTime; //交换时延
	public double end2endfinishtime; //端到端传输结束结束时刻
	public double dagschedulingtime; //DAG调度的等待时间
	public double networktransmissiontime; //网络传输时间
	public int submitVmId;
	public String submitVmName;
	public String destVmName;
	public int destVmId;
	public double submitPktSize;

	public Request request;

	public WorkloadResultWriter resultWriter;

	public boolean failed = false;
	public String msgName;

	public Workload(int workloadId, int jobId, WorkloadResultWriter writer) {
		this.workloadId = workloadId;
		this.resultWriter = writer;
		this.jobId = jobId;
	}

	public void writeResult() {
		this.resultWriter.writeResult(this);
	}

	@Override
	public int compareTo(Workload that) {
		return this.workloadId - that.workloadId;
	}

	@Override
	public String toString() {
		return "Workload (ID:"+workloadId+"/"+appId+", time:"+time+", VM:"+submitVmId;
	}
}
