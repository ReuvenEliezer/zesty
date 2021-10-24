package com.zesty.project.services;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.crypto.Data;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class InstanceServiceImpl implements InstanceService {

    private static final Logger logger = LogManager.getLogger(InstanceServiceImpl.class);

    private static final String regionsFilePath = "src/main/resources/regions.txt";
    private static final String instancesResultFilePath = "src/main/resources/<region>.json";


    private Map<Regions, List<Instance>> instancesToRegionMap = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void start() {

        String[] regionArr = getRegions();
        if (regionArr == null || regionArr.length == 0) {
            throw new IllegalArgumentException("region not valid");
        }

        /**
         * https://docs.aws.amazon.com/code-samples/latest/catalog/java-ec2-src-main-java-aws-example-ec2-DescribeInstances.java.html
         */
        for (String region : regionArr) {
            final AmazonEC2 amazonEC2 = buildDefaultEc2(region);
            List<Instance> allInstances = getAllInstances(amazonEC2);
            writeSortedInstancesByLaunchTimeToFile(allInstances, region);
        }

    }

    @Override
    public List<Instance> getSortedInstanceByLunchTime(Regions region) {
        if (!instancesToRegionMap.containsKey(region)) {
            start();
        }
        return instancesToRegionMap.get(region);
//        try {
//            return objectMapper.readValue(instancesResultFilePath.replace("<region>", region.getName()), List.class);
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
//        return null;
    }

    private void writeSortedInstancesByLaunchTimeToFile(List<Instance> allInstances, String region) {
        Collections.sort(allInstances, Comparator.comparing(Instance::getLaunchTime));
        instancesToRegionMap.put(Regions.fromName(region), allInstances);

        StringBuilder sb = new StringBuilder("[");

        for (int i = 0; i < allInstances.size(); i++) {
            try {
                String jsonInstance = objectMapper.writeValueAsString(allInstances.get(i));
                sb.append(jsonInstance);
                if (i < allInstances.size() - 1) {
                    sb.append(",");
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        sb.append("]");
        try {
            Files.write(Paths.get(instancesResultFilePath.replace("<region>", region)), sb.toString().getBytes());
        } catch (IOException e) {
            //TODO add error log
            e.printStackTrace();
        }
    }

    private List<Instance> getAllInstances(AmazonEC2 amazonEC2) {
        Date now = new Date(System.currentTimeMillis());
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

                    int diffInDays = (int) ((now.getTime() - instance.getLaunchTime().getTime()) / (1000 * 60 * 60 * 24));
                    logger.info("total days since instance was launched: {}", diffInDays);
                }
            }
            request.setNextToken(response.getNextToken());

            if (response.getNextToken() == null) {
                done = true;
            }
        }
        return instanceList;
    }

    private String[] getRegions() {
        try {
            FileInputStream fis = new FileInputStream(regionsFilePath);
            String[] split = IOUtils.toString(fis, StandardCharsets.UTF_8)
                    .split(",");
            return StringUtils.stripAll(split);
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
