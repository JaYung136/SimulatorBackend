package org.sim.workflowsim.scheduling;

import org.sim.cloudbus.cloudsim.Cloudlet;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.service.Constants;
import org.sim.workflowsim.Job;

import java.util.List;

public class StaticUserAlgorithm extends BaseSchedulingAlgorithm{

    @Override
    public void run() throws Exception {
        List<Cloudlet> cloudlets = getCloudletList();
        for(Cloudlet c: cloudlets) {
            Job job = (Job)c;
            if(job.getTaskList().size() >= 1) {
                String appName = job.getTaskList().get(0).name;
                String hostName = Constants.staticApp2Host.get(appName);
                Integer hostId = 0;
                for(Host h: Constants.hosts) {
                    if(h.getName().equals(hostName)) {
                        hostId = h.getId();
                        break;
                    }
                }
                c.setVmId(hostId);
                getScheduledList().add(c);
            }else {
                c.setVmId(0);
                getScheduledList().add(c);
            }
        }
    }
}
