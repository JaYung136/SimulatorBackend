package org.sim.workflowsim;

import org.sim.service.Constants;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.ResCloudlet;


import java.util.List;

public class MigrateHostSelector {
    public Host findHostForJob(List<Integer> excludedHosts) {
        Host ret = null;
        Double value = Double.MAX_VALUE;
        for(Host host: Constants.hosts) {
            if(excludedHosts.contains(host.getId())) {
                continue;
            }
            if(host.getUtilizationOfCpu() < Constants.cpuUp * 0.8 && host.getUtilizationOfRam() < Constants.ramUp * 0.8
                    && value > (host.getUtilizationOfCpu() + host.getUtilizationOfRam() / 2)) {
                ret = host;
                value = host.getUtilizationOfCpu() + host.getUtilizationOfRam() / 2;
            }
        }
        return ret;
    }

    public ResCloudlet choseContainerToMigrate(Host host) {
        return host.choseCloudletToMigrate();
    }

}
