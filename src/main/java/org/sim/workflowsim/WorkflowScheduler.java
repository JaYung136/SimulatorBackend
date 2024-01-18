/**
 * Copyright 2012-2013 University Of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sim.workflowsim;

import org.sim.service.Constants;
import org.sim.workflowsim.failure.FailureGenerator;
import org.sim.workflowsim.scheduling.*;
import org.sim.cloudbus.cloudsim.Cloudlet;
import org.sim.cloudbus.cloudsim.DatacenterBroker;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.Log;
import org.sim.cloudbus.cloudsim.core.CloudSim;
import org.sim.cloudbus.cloudsim.core.CloudSimTags;
import org.sim.cloudbus.cloudsim.core.SimEvent;
import org.sim.cloudbus.cloudsim.lists.VmList;
import org.sim.workflowsim.scheduling.*;
import org.sim.workflowsim.utils.Parameters;
import org.sim.workflowsim.utils.Parameters.SchedulingAlgorithm;
import org.sim.workflowsim.scheduling.*;

import java.util.*;

/**
 * WorkflowScheduler represents a algorithm acting on behalf of a user. It hides
 * VM management, as vm creation, sumbission of jobs to this VMs and destruction
 * of VMs. It picks up a scheduling algorithm based on the configuration
 *
 * @author Weiwei Chen
 * @since WorkflowSim Toolkit 1.0
 * @date Apr 9, 2013
 */
public class WorkflowScheduler extends DatacenterBroker {

    /**
     * The workflow engine id associated with this workflow algorithm.
     */
    private int workflowEngineId;

    /**
     * Created a new WorkflowScheduler object.
     *
     * @param name name to be associated with this entity (as required by
     * Sim_entity class from simjava package)
     * @throws Exception the exception
     * @pre name != null
     * @post $none
     */
    public WorkflowScheduler(String name) throws Exception {
        super(name);
    }

    /**
     * Binds this scheduler to a datacenter
     *
     * @param datacenterId data center id
     */
    public void bindSchedulerDatacenter(int datacenterId) {
        if (datacenterId <= 0) {
            Log.printLine("Error in data center id");
            return;
        }
        this.datacenterIdsList.add(datacenterId);
    }

    /**
     * Sets the workflow engine id
     *
     * @param workflowEngineId the workflow engine id
     */
    public void setWorkflowEngineId(int workflowEngineId) {
        this.workflowEngineId = workflowEngineId;
    }

    /**
     * Process an event
     *
     * @param ev a simEvent obj
     */
    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            // Resource characteristics request
            case CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST:
                processResourceCharacteristicsRequest(ev);
                break;
            // Resource characteristics answer
            case CloudSimTags.RESOURCE_CHARACTERISTICS:
                processResourceCharacteristics(ev);
                break;
            // VM Creation answer
            case CloudSimTags.VM_CREATE_ACK:
                processVmCreate(ev);
                break;
            // A finished cloudlet returned
            case WorkflowSimTags.CLOUDLET_CHECK:
                processCloudletReturn(ev);
                break;
            case CloudSimTags.CLOUDLET_RETURN:
                processCloudletReturn(ev);
                break;
            case CloudSimTags.END_OF_SIMULATION:
                shutdownEntity();
                break;
            case CloudSimTags.CLOUDLET_SUBMIT:
                processCloudletSubmit(ev);
                break;
            case WorkflowSimTags.CLOUDLET_UPDATE:
                processCloudletUpdate(ev);
                break;
            default:
                processOtherEvent(ev);
                break;
        }
    }

    /**
     * Switch between multiple schedulers. Based on algorithm.method
     *
     * @param name the SchedulingAlgorithm name
     * @return the algorithm that extends BaseSchedulingAlgorithm
     */
    private BaseSchedulingAlgorithm getScheduler(SchedulingAlgorithm name) {
        BaseSchedulingAlgorithm algorithm;

        // choose which algorithm to use. Make sure you have add related enum in
        //Parameters.java
        switch (name) {
            //by default it is Static
            case FCFS:
                algorithm = new FCFSSchedulingAlgorithm();
                break;
            case MINMIN:
                algorithm = new MinMinSchedulingAlgorithm();
                break;
            case MAXMIN:
                algorithm = new MaxMinSchedulingAlgorithm();
                break;
            case MCT:
                algorithm = new MCTSchedulingAlgorithm();
                break;
            case DATA:
                algorithm = new DataAwareSchedulingAlgorithm();
                break;
            case STATIC:
                algorithm = new StaticSchedulingAlgorithm();
                break;
            case ROUNDROBIN:
                algorithm = new RoundRobinSchedulingAlgorithm();
                break;
            case MIGRATE:
                algorithm = new SchedulingForMigrate();
                break;
            case K8S:
                algorithm = new K8sSchedulingAlgorithm();
                break;
            case USER:
                algorithm = new StaticUserAlgorithm();
                break;
            default:
                algorithm = new StaticSchedulingAlgorithm();
                break;

        }
        return algorithm;
    }

    /**
     * Process the ack received due to a request for VM creation.
     *
     * @param ev a SimEvent object
     * @pre ev != null
     * @post $none
     */
    @Override
    protected void processVmCreate(SimEvent ev) {

        int[] data = (int[]) ev.getData();
        int datacenterId = data[0];
        int vmId = data[1];
        int result = data[2];
        if(vmId == -1) {
            Log.printLine("Scheduler create vm");
            submitCloudlets();
        }
        if (result == CloudSimTags.TRUE) {
            getVmsToDatacentersMap().put(vmId, datacenterId);
            /**
             * Fix a bug of cloudsim Don't add a null to getVmsCreatedList()
             * June 15, 2013
             */
            if (VmList.getById(getVmList(), vmId) != null) {
                getVmsCreatedList().add(VmList.getById(getVmList(), vmId));
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Container #" + vmId
                        + " has been created in Host #"
                        + VmList.getById(getVmsCreatedList(), vmId).getHost().getId());
            }
        } else {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Creation of VM #" + vmId
                    + " failed in Datacenter #" + datacenterId);
        }

        incrementVmsAcks();

        // all the requested VMs have been created
        if (getVmsCreatedList().size() == getVmList().size() - getVmsDestroyed()) {
            submitCloudlets();
        } else {
            // all the acks received, but some VMs were not created
            if (getVmsRequested() == getVmsAcks()) {
                // find id of the next datacenter that has not been tried
                for (int nextDatacenterId : getDatacenterIdsList()) {
                    if (!getDatacenterRequestedIdsList().contains(nextDatacenterId)) {
                        createVmsInDatacenter(nextDatacenterId);
                        return;
                    }
                }

                // all datacenters already queried
                if (getVmsCreatedList().size() > 0) { // if some vm were created
                    submitCloudlets();
                } else { // no vms created. abort
                    Log.printLine(CloudSim.clock() + ": " + getName()
                            + ": none of the required VMs could be created. Aborting");
                    finishExecution();
                }
            }
        }
    }

    /**
     * Update a cloudlet (job)
     *
     * @param ev a simEvent object
     */
    protected void processCloudletUpdate(SimEvent ev) {

        /*BaseSchedulingAlgorithm scheduler = getScheduler(Parameters.getSchedulingAlgorithm());
        scheduler.setCloudletList(getCloudletList());
        Log.printLine(getCloudletList().size());
        scheduler.setVmList(getVmsCreatedList());
        scheduler.setHostList(getHostList());
        try {
            scheduler.run();
        } catch (Exception e) {
            Log.printLine("Error in configuring scheduler_method");
            Constants.nodeEnough = false;
            e.printStackTrace();
            return;
        }
       // Constants.AppNum = Constants.AppNum + scheduler.getScheduledList().size();
        List<Cloudlet> scheduledList = scheduler.getScheduledList();*/
        List<Cloudlet> scheduledList = new ArrayList<>();
        for(Object c: getCloudletList()) {
            Job j = (Job)c;
            // if stage-in
            if(j.getTaskList().size() == 0) {
                ((Job) c).setVmId(0);
            }else /*not*/{
                String name = j.getTaskList().get(0).name;
                for(Map.Entry<String, Integer> r: Constants.schedulerResult.entrySet()) {
                    if(r.getKey() == null) {
                        continue;
                    }
                    if(r.getKey().equals(name)) {
                        ((Job) c).setVmId(r.getValue());
                    }
                }
            }
            scheduledList.add((Cloudlet) c);
        }
        for (Cloudlet cloudlet : scheduledList) {
            int vmId = cloudlet.getVmId();
            double delay = 0.0;
            if (Parameters.getOverheadParams().getQueueDelay() != null) {
                delay = Parameters.getOverheadParams().getQueueDelay(cloudlet);
            }

            schedule(getVmsToDatacentersMap().get(0), delay, CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
        }
        getCloudletList().removeAll(scheduledList);
        getCloudletSubmittedList().addAll(scheduledList);
        cloudletsSubmitted += scheduledList.size();
    }

    /**
     * Process a cloudlet (job) return event.
     *
     * @param ev a SimEvent object
     * @pre ev != $null
     * @post $none
     */
    @Override
    protected void processCloudletReturn(SimEvent ev) {
        //Log.printLine("WorkflowScheduler process cloudlet return");
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        Job job = (Job) cloudlet;

        /**
         * Generate a failure if failure rate is not zeros.
         */
        FailureGenerator.generate(job);
        /*Random r = new Random();
        Integer t = r.nextInt(90) + 10;
        if(t > 500) {
            try {
                job.setCloudletStatus(Cloudlet.FAILED);
            } catch (Exception e) {
                Log.printLine("fail");
            }
        } else {
            try {
                job.setCloudletStatus(Cloudlet.SUCCESS);
            } catch (Exception e) {
                Log.printLine("fail");
            }
        }*/
        //Log.printLine("Job: "+ job.getCloudletId() + " " + job.getCloudletStatus());
        getCloudletReceivedList().add(cloudlet);
        getCloudletSubmittedList().remove(cloudlet);
        Host host = null;
        for(Host h: getHostList()) {
            if(h.getId() == cloudlet.getVmId()) {
                host = h;
            }
        }
        if(host == null) {
            return;
        }
        //CondorVM vm = (CondorVM) getVmsCreatedList().get(cloudlet.getVmId());
        //so that this resource is released
        host.setState(WorkflowSimTags.VM_STATUS_IDLE);

        double delay = 0.0;
        if (Parameters.getOverheadParams().getPostDelay() != null) {
            delay = Parameters.getOverheadParams().getPostDelay(job);
        }
        schedule(this.workflowEngineId, delay, CloudSimTags.CLOUDLET_RETURN, cloudlet);

        cloudletsSubmitted--;
        //not really update right now, should wait 1 s until many jobs have returned
        schedule(this.getId(), 0.0, WorkflowSimTags.CLOUDLET_UPDATE);

    }

    /**
     * Start this entity (WorkflowScheduler)
     */
    @Override
    public void startEntity() {
        //Log.printLine(getName() + " is starting...");
        // this resource should register to regional GIS.
        // However, if not specified, then register to system GIS (the
        // default CloudInformationService) entity.
        //int gisID = CloudSim.getEntityId(regionalCisName);
        int gisID = -1;
        if (gisID == -1) {
            gisID = CloudSim.getCloudInfoServiceEntityId();
        }

        // send the registration to GIS
        sendNow(gisID, CloudSimTags.REGISTER_RESOURCE, getId());
    }

    /**
     * Terminate this entity (WorkflowScheduler)
     */
    @Override
    public void shutdownEntity() {
        clearDatacenters();
        //Log.printLine(getName() + " is shutting down...");
    }

    /**
     * Submit cloudlets (jobs) to the created VMs. Scheduling is here
     */
    @Override
    protected void submitCloudlets() {
        //Log.printLine("sub " + workflowEngineId);
        sendNow(this.workflowEngineId, CloudSimTags.CLOUDLET_SUBMIT, null);
    }
    /**
     * A trick here. Assure that we just submit it once
     */
    private boolean processCloudletSubmitHasShown = false;

    /**
     * Submits cloudlet (job) list
     *
     * @param ev a simEvent object
     */
    protected void processCloudletSubmit(SimEvent ev) {
        List<Job> list = (List) ev.getData();
        getCloudletList().addAll(list);

        sendNow(this.getId(), WorkflowSimTags.CLOUDLET_UPDATE);
        if (!processCloudletSubmitHasShown) {
            processCloudletSubmitHasShown = true;
        }
    }

    /**
     * Process a request for the characteristics of a PowerDatacenter.
     *
     * @param ev a SimEvent object
     * @pre ev != $null
     * @post $none
     */
    @Override
    protected void processResourceCharacteristicsRequest(SimEvent ev) {
        setDatacenterCharacteristicsList(new HashMap<>());
        Log.printLine(CloudSim.clock() + ": " + getName() + ": Cloud Resource List received with "
                + getDatacenterIdsList().size() + " resource(s)");
        for (Integer datacenterId : getDatacenterIdsList()) {
            sendNow(datacenterId, CloudSimTags.RESOURCE_CHARACTERISTICS, getId());
        }
    }
}
