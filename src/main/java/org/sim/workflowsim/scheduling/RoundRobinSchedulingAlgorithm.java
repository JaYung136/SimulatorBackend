/**
 * Copyright 2013-2014 University Of Southern California
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
import org.sim.cloudbus.cloudsim.Log;

import org.sim.workflowsim.Job;

import java.util.*;

/**
 * The Round Robin algorithm.
 *
 * @author Weiwei Chen
 * @since WorkflowSim Toolkit 1.0
 * @date May 12, 2014
 */
public class RoundRobinSchedulingAlgorithm extends BaseSchedulingAlgorithm {

    private Map<Integer, Integer> pes = new HashMap<>();

    /**
     * The main function
     */
    @Override
    public void run() {
        for(Host h: Constants.hosts) {
            pes.put(h.getId(), h.getNumberOfPes());
        }
        int vmIndex = 0;
        
        int size = getCloudletList().size();
        Collections.sort(getCloudletList(), new CloudletListComparator());
        List vmList = getHostList();
        Collections.sort(vmList, new VmListComparator());
        for (int j = 0; j < size; j++) {
            Cloudlet cloudlet = (Cloudlet) getCloudletList().get(j);
            int vmSize = vmList.size();
            Host firstIdleVm = null;//(CondorVM)getVmList().get(0);
            for (int l = 0; l < vmSize; l++) {
                Host vm = (Host) vmList.get(l);
                if (pes.get(vm.getId()) >= ((Job)cloudlet).getNumberOfPes()) {
                    firstIdleVm = vm;
                    break;
                }
            }
            if (firstIdleVm == null) {
                break;
            }
            //((Host) firstIdleVm).setState(WorkflowSimTags.VM_STATUS_BUSY);
            cloudlet.setVmId(firstIdleVm.getId());
            pes.put(firstIdleVm.getId(), pes.get(firstIdleVm.getId()) - ((Job)cloudlet).getNumberOfPes());
            getScheduledList().add(cloudlet);
            vmIndex = (vmIndex + 1) % vmList.size();
            Log.printLine("Schedule Task " + cloudlet.getCloudletId() + " ---> Host " + firstIdleVm.getId());
        }
    }
    /**
     * Sort it based on vm index
     */
    public class VmListComparator implements Comparator<Host>{
        @Override
        public int compare(Host v1, Host v2){
            return Integer.compare(v1.getId(), v2.getId());
        }
    }
    
    public class CloudletListComparator implements Comparator<Cloudlet>{
        @Override
        public int compare(Cloudlet c1, Cloudlet c2){
            return Integer.compare(c1.getCloudletId(), c2.getCloudletId());
        }
    }    
}

