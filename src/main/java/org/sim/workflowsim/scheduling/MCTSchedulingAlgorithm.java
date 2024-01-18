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
import org.sim.cloudbus.cloudsim.Log;

import org.sim.workflowsim.WorkflowSimTags;

/**
 * MCT algorithm
 *
 * @author Weiwei Chen
 * @since WorkflowSim Toolkit 1.0
 * @date Apr 9, 2013
 */
public class MCTSchedulingAlgorithm extends BaseSchedulingAlgorithm {

    public MCTSchedulingAlgorithm() {
        super();
    }

    @Override
    public void run() {


        int size = getCloudletList().size();

        for (int i = 0; i < size; i++) {
            Cloudlet cloudlet = (Cloudlet) getCloudletList().get(i);
            int vmSize = Constants.hosts.size();
            Host firstIdleVm = null;

            for (int j = 0; j < vmSize; j++) {
                Host vm = (Host) Constants.hosts.get(j);
                if (vm.getState() == WorkflowSimTags.VM_STATUS_IDLE) {
                    firstIdleVm = vm;
                    break;
                }
            }
            if (firstIdleVm == null) {
                break;
            }

            for (int j = 0; j < vmSize; j++) {
                Host vm = (Host) Constants.hosts.get(j);
                if ((vm.getState() == WorkflowSimTags.VM_STATUS_IDLE)
                        && (vm.getTotalMips() > firstIdleVm.getTotalMips())) {
                    firstIdleVm = vm;
                }
            }
            firstIdleVm.setState(WorkflowSimTags.VM_STATUS_BUSY);
            cloudlet.setVmId(firstIdleVm.getId());
            getScheduledList().add(cloudlet);
            Log.printLine("Schedules " + cloudlet.getCloudletId() + " with "
                    + cloudlet.getCloudletLength() + " to Host " + firstIdleVm.getId()
                    + " with " + firstIdleVm.getTotalMips());
        }
    }
}
