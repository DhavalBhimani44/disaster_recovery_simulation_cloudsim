package simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;

import java.text.DecimalFormat;
import java.util.*;

public class DisasterRecoverySimulation {

    public static void main(String[] args) {
        Log.printLine("Starting Disaster Recovery Simulation...");

        try {
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            CloudSim.init(numUsers, calendar, traceFlag);

            Datacenter primaryDC = createDatacenter("PrimaryDC");
            Datacenter backupDC = createDatacenter("BackupDC");

            DatacenterBroker broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            List<Vm> vmList = createVMs(brokerId, 4);
            List<Cloudlet> cloudletList = createCloudlets(brokerId, 6);

            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);

            CloudSim.startSimulation();

            List<Cloudlet> completedCloudlets = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            simulateDisasterAndRecovery(broker, completedCloudlets, backupDC);

            printCloudletList(completedCloudlets);
            Log.printLine("Disaster Recovery Simulation completed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Simulation failed due to an error.");
        }
    }

    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();
        int mips = 1000;
        int ram = 2048;
        long storage = 1000000;
        int bw = 10000;

        for (int i = 0; i < 2; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));

            hostList.add(new Host(
                i,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList)
            ));
        }

        String arch = "x86"; 
        String os = "Linux"; 
        String vmm = "Xen";
        double timeZone = 10.0;
        double costPerSec = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, timeZone, costPerSec, costPerMem, costPerStorage, costPerBw
        );

        return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
    }

    private static List<Vm> createVMs(int userId, int numVMs) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < numVMs; i++) {
            Vm vm = new Vm(i, userId, 1000, 1, 1024, 1000, 10000, "Xen", new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int userId, int numCloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long length = 40000;
        int pesNumber = 1;
        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel utilization = new UtilizationModelFull();

        for (int i = 0; i < numCloudlets; i++) {
            Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilization, utilization, utilization);
            cloudlet.setUserId(userId);
            list.add(cloudlet);
        }
        return list;
    }

    private static void simulateDisasterAndRecovery(DatacenterBroker broker, List<Cloudlet> completedCloudlets, Datacenter backupDC) throws Exception {
        Log.printLine("\n[SIMULATION] Simulating failure of VM 0 and recovery...");

        List<Cloudlet> recovered = new ArrayList<>();

        for (Cloudlet cloudlet : completedCloudlets) {
            if (cloudlet.getVmId() == 0 && cloudlet.getStatus() != Cloudlet.SUCCESS) {
                int newCloudletId = cloudlet.getCloudletId() + 100;
                Cloudlet newCloudlet = new Cloudlet(
                    newCloudletId,
                    cloudlet.getCloudletLength(),
                    cloudlet.getNumberOfPes(),
                    cloudlet.getCloudletFileSize(),
                    cloudlet.getCloudletOutputSize(),
                    new UtilizationModelFull(),
                    new UtilizationModelFull(),
                    new UtilizationModelFull()
                );
                newCloudlet.setUserId(cloudlet.getUserId());

                Vm backupVm = new Vm(99, broker.getId(), 1000, 1, 1024, 1000, 10000, "Xen", new CloudletSchedulerTimeShared());
                broker.submitVmList(Collections.singletonList(backupVm));
                broker.submitCloudletList(Collections.singletonList(newCloudlet));

                CloudSim.startSimulation();
                CloudSim.stopSimulation();

                recovered.addAll(broker.getCloudletReceivedList());
            }
        }

        completedCloudlets.addAll(recovered);
        Log.printLine("[SIMULATION] Recovery complete. Re-executed failed cloudlets on backup VM.");
    }

    private static void printCloudletList(List<Cloudlet> list) {
        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
            "DataCenter ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");

        for (Cloudlet cloudlet : list) {
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");

                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                    indent + indent + dft.format(cloudlet.getActualCPUTime()) +
                    indent + dft.format(cloudlet.getExecStartTime()) +
                    indent + dft.format(cloudlet.getFinishTime()));
            } else {
                Log.printLine("FAILED");
            }
        }
    }
}
