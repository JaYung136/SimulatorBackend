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
package org.sim.workflowsim.scheduling;

import org.sim.service.Constants;
import org.sim.cloudbus.cloudsim.Cloudlet;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.Vm;


import java.util.ArrayList;
import java.util.List;

/**
 * The base scheduler has implemented the basic features. Every other scheduling method
 * should extend from BaseSchedulingAlgorithm but should not directly use it. 
 *
 * @author Weiwei Chen
 * @since WorkflowSim Toolkit 1.0
 * @date Apr 9, 2013
 */
public abstract class BaseSchedulingAlgorithm implements SchedulingAlgorithmInterface {
<<<<<<< HEAD
=======

>>>>>>> 5ae75145f27ee66af79323908ebf18fed3de314a
    /**
     * the job list.
     */
    private List<? extends Cloudlet> cloudletList;
    /**
     * the vm list.
     */
    private List<? extends Vm> vmList;

    private List<? extends Host> hostList;
    /**
     * the scheduled job list.
     */
    private List< Cloudlet> scheduledList;

    /**
     * Initialize a BaseSchedulingAlgorithm
     */
    public BaseSchedulingAlgorithm() {
        this.scheduledList = new ArrayList();
    }

    /**
     * Sets the job list.
     *
     * @param list
     */
    @Override
    public void setCloudletList(List list) {
        this.cloudletList = list;
    }

    /**
     * Sets the vm list
     *
     * @param list
     */
    @Override
    public void setVmList(List list) {
        this.vmList = new ArrayList(list);
    }

    public void setHostList(List list) {
        this.hostList = new ArrayList<>(list);
    }


    /**
     * Gets the job list.
     *
     * @return the job list
     */
    @Override
    public List getCloudletList() {
        return this.cloudletList;
    }

    /**
     * Gets the vm list
     *
     * @return the vm list
     */
    @Override
    public List getVmList() {
        return this.vmList;
    }

    public List getHostList() {return Constants.hosts;}

    /**
     * The main function
     * @throws Exception
     */
    @Override
    public abstract void run() throws Exception;

    /**
     * Gets the scheduled job list
     *
     * @return job list
     */
    @Override
    public List getScheduledList() {
        return this.scheduledList;
    }
}
