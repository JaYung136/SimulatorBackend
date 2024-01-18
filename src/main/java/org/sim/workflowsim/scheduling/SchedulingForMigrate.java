package org.sim.workflowsim.scheduling;

import org.sim.cloudbus.cloudsim.Cloudlet;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.Log;
import org.sim.service.Constants;
import org.sim.workflowsim.Job;

import java.util.Collections;
import java.util.List;

public class SchedulingForMigrate extends BaseSchedulingAlgorithm{
    @Override
    public void run() throws Exception {
        int size = getCloudletList().size();
        Host choseHost = null;
        for(Host h: Constants.hosts) {
            if(choseHost == null || h.getTotalMips() > choseHost.getTotalMips()) {
                choseHost = h;
            }
        }
        for(int i = 0; i < size; i++) {
            Cloudlet cloudlet = (Cloudlet) getCloudletList().get(i);
            cloudlet.setVmId(choseHost.getId());
            getScheduledList().add(cloudlet);
        }
    }
}
