package org.sim.cloudbus.cloudsim;

import java.util.ArrayList;
import java.util.List;

public class VmAllocationPolicyK8s extends VmAllocationPolicySimple{
    /**
     * Creates the new VmAllocationPolicySimple object.
     *
     * @param list the list
     * @pre $none
     * @post $none
     */
    public VmAllocationPolicyK8s(List<? extends Host> list) {
        super(list);
    }

    private double leastRequestedPriority(Host host) {
        double cpu_score = (double) (host.getVmScheduler().getAvailableMips()) / (double) (host.getNumberOfPes() * host.getVmScheduler().getPeCapacity());
        //Log.printLine("cpu_score: " + cpu_score);
        double ram_score = (double) (host.getRamProvisioner().getAvailableRam()) / (double) host.getRamProvisioner().getRam();
        //Log.printLine("ram_score: " + ram_score);
        return 10 * (cpu_score + ram_score) / 2;
    }

    private double balancedResourceAllocation(Host host) {
        double cpu_fraction = 1 -  (host.getVmScheduler().getAvailableMips()) / (double) (host.getNumberOfPes() * host.getVmScheduler().getPeCapacity());
        //Log.printLine("cpu_: " + cpu_fraction);
        double ram_fraction = 1 - (double) (host.getRamProvisioner().getAvailableRam()) / (double) host.getRamProvisioner().getRam();
        //Log.printLine("ram: " + ram_fraction);
        double mean = (cpu_fraction + ram_fraction) / 2;
        //Log.printLine("mean: " + mean);
        double variance = ((cpu_fraction - mean)*(cpu_fraction - mean)
                + (ram_fraction - mean)*(ram_fraction - mean)
        ) / 2;
        //Log.printLine("variance: " + variance);
        return 10 - variance * 10;
    }

    private double getScore(Host host) {
        return (balancedResourceAllocation(host) + leastRequestedPriority(host)) / 2;
    }

    @Override
    public boolean scheduleAll() {
        for(Vm v: getContainerList()) {
            if(!allocateHostForVm(v))
                return false;
        }
        return true;
    }
    @Override
    public boolean allocateHostForVm(Vm vm) {
        int requiredPes = vm.getNumberOfPes();
        boolean result = false;
        int tries = 0;
        List<Integer> freePesTmp = new ArrayList<Integer>();
        for (Integer freePes : getFreePes()) {
            freePesTmp.add(freePes);
        }
        Boolean ifStatic = true;
        Log.printLine("k8s调度");
        if (!getVmTable().containsKey(vm.getUid())) { // if this vm was not created
            do {// we still trying until we find a host or until we try all of them
                double moreFree = Double.MIN_VALUE;
                int idx = -1;
                //Log.printLine(freePesTmp.size());
                if(vm.getHost() != null && ifStatic) {
                    idx = vm.getHost().getId();
                    Log.printLine("静态调度");
                    ifStatic = false;
                } else {
                    for (int i = 0; i < freePesTmp.size(); i++) {
                        //Log.printLine(getScore(getHostList().get(i)));
                        if (freePesTmp.get(i) == Integer.MIN_VALUE) {
                            continue;
                        }
                        if (getScore(getHostList().get(i)) > moreFree) {
                            moreFree = getScore(getHostList().get(i));
                            idx = i;
                        }
                    }
                }

                if(idx == -1) {
                    return false;
                }
                Host host = getHostList().get(idx);
                result = host.vmCreate(vm);

                if (result) { // if vm were succesfully created in the host
                    getVmTable().put(vm.getUid(), host);
                    getUsedPes().put(vm.getUid(), requiredPes);
                    getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
                    result = true;
                    break;
                } else {
                    freePesTmp.set(idx, Integer.MIN_VALUE);
                }
                tries++;
            } while (!result && tries < getFreePes().size());
        }
        return result;
    }
}
