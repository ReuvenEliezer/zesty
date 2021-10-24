package com.zesty.project.services;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class InstanceServiceImpl implements InstanceService {

    private static final Logger logger = LogManager.getLogger(InstanceServiceImpl.class);

    private static final String regionsFilePath = "src/main/resources/regions.txt";
    private static final String instancesResultFilePath = "src/main/resources/regions.json";

    @Override
    public void start() {
        String data = getRegion();
        if (data == null) {
            throw new IllegalArgumentException("region not valid");
        }

        /**
         * https://docs.aws.amazon.com/code-samples/latest/catalog/java-ec2-src-main-java-aws-example-ec2-DescribeInstances.java.html
         */
        final AmazonEC2 amazonEC2 = buildDefaultEc2(data);

        List<Instance> allInstances = getAllInstances(amazonEC2);
        writeSortedInstancesByLaunchTimeToFile(allInstances);
    }

    private void writeSortedInstancesByLaunchTimeToFile(List<Instance> allInstances) {
        Collections.sort(allInstances, Comparator.comparing(Instance::getLaunchTime));
        StringBuilder sb = new StringBuilder();
        for (Instance instance : allInstances) {
            sb.append(instance.toString());
        }
        try {
            Files.write(Paths.get(instancesResultFilePath), sb.toString().getBytes());
        } catch (IOException e) {
            //TODO add error log
            e.printStackTrace();
        }
    }

    private List<Instance> getAllInstances(AmazonEC2 amazonEC2) {
        List<Instance> instanceList = new ArrayList<>();
        boolean done = false;
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        while (!done) {
            DescribeInstancesResult response = amazonEC2.describeInstances(request);
            for (Reservation reservation : response.getReservations()) {
                for (Instance instance : reservation.getInstances()) {
                    logger.info(
                            "Found instance with id {}, " +
                                    "AMI {}, " +
                                    "type {}, " +
                                    "state {} " +
                                    "monitoring state {}" +
                                    "LaunchTime {}",
                            instance.getInstanceId(),
                            instance.getImageId(),
                            instance.getInstanceType(),
                            instance.getState().getName(),
                            instance.getMonitoring().getState(),
                            instance.getLaunchTime());
                    instanceList.add(instance);
                }
            }
            request.setNextToken(response.getNextToken());

            if (response.getNextToken() == null) {
                done = true;
            }
        }
        return instanceList;
    }

    private String getRegion() {
        try {
            FileInputStream fis = new FileInputStream(regionsFilePath);
            return IOUtils.toString(fis, StandardCharsets.UTF_8)
                    .trim();
        } catch (FileNotFoundException e) {
            //TODO add error log
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("region not valid");
    }


    private static AmazonEC2 buildDefaultEc2(String data) {
        AWSCredentialsProvider credentials = new EnvironmentVariableCredentialsProvider();
        if (credentials.getCredentials().getAWSAccessKeyId() == null || credentials.getCredentials().getAWSSecretKey() == null) {
            throw new IllegalArgumentException("please set  System.getenv(\"AWS_ACCESS_KEY_ID\"); System.getenv(\"AWS_SECRET_KEY\"); System.getenv(\"AWS_SESSION_TOKEN\")");
        }
        return AmazonEC2ClientBuilder.standard()
                .withCredentials(credentials)
                .withRegion(Regions.fromName(data))
                .build();
    }
}
