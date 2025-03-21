package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;

// Ping
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class AutoScaler implements Runnable{

    private static String AWS_REGION = "eu-central-1";
    private static String AMI_ID = "ami-08d69ef39c1ef2da5";
    private static String KEY_NAME = "mykeypair";
    private static String SEC_GROUP_ID = "launch-wizard-1";

    // Time to wait until the instance is terminated (in milliseconds).
    private static long heartbeat = 1000 * 80;
    public static double MAX_THRESHOLD = 60.0;
    public static double MIN_THRESHOLD = 30.0;
    private static Integer OBS_TIME = 60 * 1000 * 2;
    private static Integer PERIOD = 60 * 2;

    private static ConcurrentHashMap<Instance, Double> cpuInstances = new ConcurrentHashMap<>();

    public static int ping(Instance instance) {
        String ip = instance.getPublicIpAddress();
        String urlString = "http://" + ip + ":" + "8080" + "/";
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // Set timeout to 5 seconds
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Ping successful. Server is up. Instance " + instance.getInstanceId() + " is running the webserver.");
                return 200;
            } else {
                System.out.println("Ping failed. Server responded with code: " + responseCode);
                return responseCode;
            }
        } catch (IOException e) {
            System.out.println("Ping failed. Unable to reach server.");
            return 404;
        }
    }


    public static void updateCpuUsage(Instance instance, Dimension instanceDimension, AmazonCloudWatch cloudWatch) {
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest().withStartTime(new Date(new Date().getTime() - OBS_TIME))
            .withNamespace("AWS/EC2")
            .withPeriod(PERIOD)
            .withMetricName("CPUUtilization")
            .withStatistics("Average")
            .withDimensions(instanceDimension)
            .withEndTime(new Date());
        
        List<Datapoint> datapoints = cloudWatch.getMetricStatistics(request).getDatapoints();
        if (datapoints.size() == 0) {
            System.out.println("No datapoints found for instance " + instance.getInstanceId());
            cpuInstances.put(instance, -1.0);
            return;
        }
        cpuInstances.put(instance, datapoints.get(0).getAverage());
        System.out.println("Instance " + instance.getInstanceId() + " CPU usage: " + cpuInstances.get(instance));
    }

    public static List<Instance> updateRunningInstances(AmazonEC2 ec2, AmazonCloudWatch cloudWatch) {
        List<Instance> instances = new ArrayList<>();
        try {
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);
            List<Reservation> reservations = ec2.describeInstances().getReservations();
            for (Reservation reservation : reservations) {
                for (Instance instance : reservation.getInstances()) {
                    if (instance.getState().getName().equals("running")) {
                        if(ping(instance) != 200) {
                            System.out.println("Instance " + instance.getInstanceId() + " is not running the webserver (Failed to reply to ping).");
                            continue;
                        }
                        instances.add(instance);
                        instanceDimension.setValue(instance.getInstanceId());
                        updateCpuUsage(instance, instanceDimension, cloudWatch);
                        if (!LoadBalancerHandler.currentInstances.containsKey(instance)) {
                            LoadBalancerHandler.currentInstances.put(instance, 0L);
                            LoadBalancerHandler.activeInstances.add(instance);
                        }
                    }
                }
            }
        } catch (AmazonServiceException e) {
            System.out.println(e.getErrorMessage());
        }
        // Get instances that are not running anymore
        List<Instance> toRemove = new ArrayList<>();
        for (Instance instance : LoadBalancerHandler.currentInstances.keySet()) {
            System.out.println("Instance ID: " + instance.getInstanceId() + " Workload: " + LoadBalancerHandler.currentInstances.get(instance));
            if (!instances.contains(instance)) {
                toRemove.add(instance);
            }
        }
        for (Instance instance : toRemove) {
            LoadBalancerHandler.currentInstances.remove(instance);
            cpuInstances.remove(instance);
        }

        return instances;
    }

    public static void inScaler() {
        double min = 100;
        Instance instance = null;
        for (Instance i : cpuInstances.keySet()) {
            double cpu = cpuInstances.get(i);
            if (cpu >= 0 && cpu < min) {
                min = cpu;
                instance = i;
            }
        }
        if (instance != null && min < MIN_THRESHOLD && cpuInstances.size() > 1) {
            LoadBalancerHandler.activeInstances.remove(instance);
            if (LoadBalancerHandler.currentInstances.get(instance) == 0) {
                System.out.println("Terminating instance " + instance.getInstanceId());
                TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
                termInstanceReq.withInstanceIds(instance.getInstanceId());
                LoadBalancerHandler.ec2.terminateInstances(termInstanceReq);
                LoadBalancerHandler.currentInstances.remove(instance);
                cpuInstances.remove(instance);
            }
        }
    }

    public static Boolean checkInstanceOverload() {
        if (LoadBalancerHandler.currentInstances.size() == 0) {
            return true;
        }
        double max = 0;
        Instance maxInstance = null;
        for (Instance i : cpuInstances.keySet()) {
            if (cpuInstances.get(i) > max) {
                max = cpuInstances.get(i);
                maxInstance = i;
            }
        }
        if (maxInstance != null && max > MAX_THRESHOLD) {
            System.out.println("Max threshold reached. " + MAX_THRESHOLD);
            return true;
        }
        return false;
    }

    public static void outScaler() {
        if (!checkInstanceOverload()) {
            return;
        }
        System.out.println("Starting a new instance.");
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withLaunchTemplate(new LaunchTemplateSpecification()
            .withLaunchTemplateName("cnv-final-config").withVersion("1"))
            .withMinCount(1)
            .withMaxCount(1);
        RunInstancesResult runInstancesResult = LoadBalancerHandler.ec2.runInstances(runInstancesRequest);
        Instance currInstance = runInstancesResult.getReservation().getInstances().get(0);
        System.out.println("New instance created: " + currInstance.getInstanceId());
    }

    @Override
    public void run() {
        try {
            AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
            AmazonCloudWatch cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();

            DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
            System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() + " Availability Zones.");
            int numInstances = ec2.describeInstances().getReservations().size();
            System.out.println("You have " + numInstances + " Amazon EC2 instance(s) running.");
            while (true) {
                System.out.println("\nUpdating running instances...");
                updateRunningInstances(ec2, cloudWatch);
                // Check if we need to terminate an instance or start a new one
                inScaler();
                outScaler();
                try {
                    Thread.sleep(heartbeat);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }
}
