/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.physicalcomponents;

import org.sim.cloudsimsdn.*;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.provisioners.BwProvisioner;
import org.sim.cloudsimsdn.provisioners.RamProvisioner;
import org.sim.cloudsimsdn.sdn.LogWriter;
import org.sim.cloudsimsdn.sdn.VmSchedulerTimeSharedOverSubscriptionDynamicVM;
import org.sim.cloudsimsdn.sdn.monitor.MonitoringValues;
import org.sim.cloudsimsdn.sdn.monitor.power.PowerUtilizationEnergyModelHostLinear;
import org.sim.cloudsimsdn.sdn.monitor.power.PowerUtilizationMonitor;
import org.sim.cloudsimsdn.sdn.virtualcomponents.ForwardingRule;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;

import java.util.HashMap;
import java.util.List;


/**
 * Extended class of Host to support SDN.
 * Added function includes data transmission after completion of Cloudlet compute processing.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNHost extends Host implements Node {
	private ForwardingRule forwardingTable;
	private RoutingTable routingTable;
	private int rank = -1;

	private String name = null;

	private HashMap<Node, Link> linkToNextHop = new HashMap<Node, Link>();

	public SDNHost(
			RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner,
			long storage,
			List<? extends Pe> peList,
			VmScheduler vmScheduler,
			String name){
		super(NodeUtil.assignAddress(), ramProvisioner, bwProvisioner, storage,peList,vmScheduler);

		this.forwardingTable = new ForwardingRule();
		this.routingTable = new RoutingTable();
		this.name = name;
	}

	/**
	 * Requests updating of processing of cloudlets in the VMs running in this host.
	 *
	 * @param currentTime the current time
	 * @return expected time of completion of the next cloudlet in all VMs in this host.
	 *         Double.MAX_VALUE if there is no future events expected in this host
	 * @pre currentTime >= 0.0
	 * @post $none
	 */
	public double updateVmsProcessing(double currentTime) {
		double smallerTime = Double.MAX_VALUE;

		// Update VM's processing for the previous time.
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			List<Double> mipsAllocated = getVmScheduler().getAllocatedMipsForVm(vm);

//			System.err.println(CloudSim.clock()+":"+vm + " is allocated: "+ mipsAllocated);
			vm.updateVmProcessing(currentTime, mipsAllocated);
		}

		// Change MIPS share proportion depending on the remaining Cloudlets.
		adjustMipsShare();

		// Check the next event time based on the updated MIPS share proportion
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			List<Double> mipsAllocatedAfter = getVmScheduler().getAllocatedMipsForVm(vm);

//			System.err.println(CloudSim.clock()+":"+vm + " is reallocated: "+ mipsAllocatedAfter);
			double time = vm.updateVmProcessing(currentTime, mipsAllocatedAfter);

			if (time > 0.0 && time < smallerTime) {
				smallerTime = time;
			}
		}

		return smallerTime;
	}

	public void adjustMipsShare() {
		if(getVmScheduler() instanceof VmSchedulerTimeSharedOverSubscriptionDynamicVM){
			VmSchedulerTimeSharedOverSubscriptionDynamicVM sch = (VmSchedulerTimeSharedOverSubscriptionDynamicVM) getVmScheduler();
			double scaleFactor = sch.redistributeMipsDueToOverSubscriptionDynamic();

			logOverloadLogger(scaleFactor);
			for (SDNVm vm : this.<SDNVm>getVmList()) {
				vm.logOverloadLogger(scaleFactor);
			}
		}
	}

	// Check how long this Host is overloaded (The served capacity is less than the required capacity)
	private double overloadLoggerPrevTime =0;
	private double overloadLoggerPrevScaleFactor= 1.0;
	private double overloadLoggerTotalDuration =0;
	private double overloadLoggerOverloadedDuration =0;
	private double overloadLoggerScaledOverloadedDuration =0;

	private void logOverloadLogger(double scaleFactor) {
		// scaleFactor == 1 means enough resource is served
		// scaleFactor < 1 means less resource is served (only requested * scaleFactor is served)
		double currentTime = CloudSim.clock();
		double duration = currentTime - overloadLoggerPrevTime;

		if(scaleFactor > 1) {
			System.err.println("scale factor cannot be >1!");
			System.exit(1);
		}

		if(duration > 0) {
			if(overloadLoggerPrevScaleFactor < 1.0) {
				// Host was overloaded for the previous time period
				overloadLoggerOverloadedDuration += duration;
			}
			overloadLoggerTotalDuration += duration;
			overloadLoggerScaledOverloadedDuration += duration * overloadLoggerPrevScaleFactor;
			updateOverloadMonitor(currentTime, overloadLoggerPrevScaleFactor);
		}
		overloadLoggerPrevTime = currentTime;
		overloadLoggerPrevScaleFactor = scaleFactor;
	}

	public double overloadLoggerGetOverloadedDuration() {
		return overloadLoggerOverloadedDuration;
	}
	public double overloadLoggerGetTotalDuration() {
		return overloadLoggerTotalDuration;
	}
	public double overloadLoggerGetScaledOverloadedDuration() {
		return overloadLoggerScaledOverloadedDuration;
	}

	public double overloadLoggerGetOverloadedDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetOverloadedDuration();
		}
		return total;
	}
	public double overloadLoggerGetTotalDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetTotalDuration();
		}
		return total;
	}
	public double overloadLoggerGetScaledOverloadedDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetScaledOverloadedDuration();
		}
		return total;
	}

	// For monitor
	private MonitoringValues mvOverload = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);

	private void updateOverloadMonitor(double logTime, double scaleFactor) {
		double scaleReverse = (scaleFactor != 0 ? 1/scaleFactor : Float.POSITIVE_INFINITY);
		mvOverload.add(scaleReverse, logTime);
	}

	public MonitoringValues getMonitoringValuesOverloadMonitor() {
		return mvOverload;
	}

	public Vm getVm(int vmId) {
		for (Vm vm : getVmList()) {
			if (vm.getId() == vmId) {
				return vm;
			}
		}
		return null;
	}

	public boolean isSuitableForVm(Vm vm) {
		if (getStorage() < vm.getSize()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by storage");
			return false;
		}

		if (!getRamProvisioner().isSuitableForVm(vm, vm.getCurrentRequestedRam())) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by RAM");
			return false;
		}

		if (!getBwProvisioner().isSuitableForVm(vm, vm.getBw())) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by BW");
			return false;
		}

		if(getVmScheduler().getPeCapacity() < vm.getCurrentRequestedMaxMips()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
			+ " failed by PE Capacity");
			return false;
		}

		if(getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
			+ " failed by Available MIPS");
			return false;
		}

		if(getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
			+ " failed by Available MIPS");
			return false;
		}
		return true;
	}

	/******* Routeable interface implementation methods ******/

	@Override
	public int getAddress() {
		return super.getId();
	}

	@Override
	public long getBandwidth() {
		return getBw();
	}

	public long getAvailableBandwidth() {
		return getBwProvisioner().getAvailableBw();
	}

	@Override
	public void clearCnRoutingTable(){
		this.forwardingTable.clear();
	}

	@Override
	public void addCnRoute(int src, int dest, int flowId, Node to){
		forwardingTable.addRule(src, dest, flowId, to);
	}

	@Override
	public Node getCnRoute(int src, int dest, int flowId){
		Node route= this.forwardingTable.getRoute(src, dest, flowId);
		if(route == null) {
			this.printCnRoute();
			System.err.println(toString()+" getVMRoute(): ERROR: Cannot find route:" + src + "->"+dest + ", flow ="+flowId);
		}

		return route;
	}

	@Override
	public void removeCnRoute(int src, int dest, int flowId){
		forwardingTable.removeRule(src, dest, flowId);
	}

	@Override
	public void setRank(int rank) {
		this.rank=rank;
	}

	@Override
	public int getRank() {
		return rank;
	}

	@Override
	public void printCnRoute() {
		forwardingTable.printForwardingTable(getName());
	}

	public String toString() {
		return this.getName();
	}

	@Override
	public void addLink(Link l) {
		this.linkToNextHop.put(l.getOtherNode(this), l);
	}

	@Override
	public void updateNetworkUtilization() {
		// TODO Auto-generated method stub

	}

	@Override
	public void addRoute(Node destHost, Link to) {
		this.routingTable.addRoute(destHost, to);

	}

	@Override
	public List<Link> getRoute(Node destHost) {
		return this.routingTable.getRoute(destHost);
	}

	@Override
	public RoutingTable getRoutingTable() {
		return this.routingTable;
	}

	// For monitor
	private MonitoringValues mv = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringProcessedMIsPerUnit = 0;

	private PowerUtilizationMonitor powerMonitor = new PowerUtilizationMonitor(new PowerUtilizationEnergyModelHostLinear());
	public double getConsumedEnergy() {
		return powerMonitor.getTotalEnergyConsumed();
	}

	public void updateMonitor(double logTime, double timeUnit) {
		long capacity = (long) (this.getTotalMips() *timeUnit);
		double utilization = (double)monitoringProcessedMIsPerUnit / capacity / Consts.MILLION;
		mv.add(utilization, logTime);

		monitoringProcessedMIsPerUnit = 0;

		LogWriter log = LogWriter.getLogger("host_utilization.csv");
		log.printLine(this.getName()+","+logTime+","+utilization);

		double energy = powerMonitor.addPowerConsumption(logTime, utilization);
		LogWriter logEnergy = LogWriter.getLogger("host_energy.csv");
		logEnergy.printLine(this.getName()+","+logTime+","+energy);

		// Also update hosting VMs in this machine
		updateVmMonitor(timeUnit);
	}

	private void updateVmMonitor(double timeUnit) {
		for(Vm vm: getVmList()) {
			SDNVm tvm = (SDNVm)vm;
			tvm.updateMonitor(CloudSim.clock(), timeUnit);
		}
	}

	public MonitoringValues getMonitoringValuesHostCPUUtilization() {
		return mv;
	}

	public void increaseProcessedMIs(long processedMIs) {
//		System.err.println(this.toString() +","+ processedMIs);
		this.monitoringProcessedMIsPerUnit += processedMIs;
	}

	public MonitoringValues getMonitoringValuesHostBwUtilization() {
		if(linkToNextHop.size() != 1) {
			System.err.println(this+": Multiple links found!!");
		}

		if(linkToNextHop.size() > 0) {
			return linkToNextHop.values().iterator().next().getMonitoringValuesLinkUtilizationUp();
		}
		return null;
	}

	@Override
	public Link getLinkTo(Node nextHop) {
		return this.linkToNextHop.get(nextHop);
	}

	public String getName() {
		return name;
	}
}
