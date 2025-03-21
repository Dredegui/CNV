package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.xspec.S;

import java.util.ArrayList;
import java.util.List;

public class DynamoDBClient {
    private static AmazonDynamoDB dynamoDB;

    public DynamoDBClient() {
        try {
            dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion("eu-central-1")
            .build();
        } catch (Exception e) {
            System.err.println("Failed to create AmazonDynamoDB client; " + e.getMessage());
        }
    }

    public List<double[]> fetchDataRaytracer(String tableName) {
        List<double[]> data = new ArrayList<>();
        try {
            ScanRequest scanRequest = new ScanRequest()
                .withTableName(tableName);

            ScanResult result = dynamoDB.scan(scanRequest);
            // Parameters: AA, MULTI, SCOLS, SROWS, WCOLS, WROWS, COFF, ROFF
            for (java.util.Map<String, AttributeValue> item : result.getItems()) {
                double[] entry = new double[9];
                entry[0] = Double.parseDouble(item.get("scols").getN());
                entry[1] = Double.parseDouble(item.get("srows").getN());
                entry[2] = Double.parseDouble(item.get("wcols").getN());
                entry[3] = Double.parseDouble(item.get("wrows").getN());
                entry[4] = Double.parseDouble(item.get("coff").getN());
                entry[5] = Double.parseDouble(item.get("roff").getN());
                entry[6] = Double.parseDouble(item.get("imageSize").getN());
                entry[7] = Double.parseDouble(item.get("texmapSize").getN());
                entry[8] = Double.parseDouble(item.get("workload").getN());
                data.add(entry);
            }
        } catch (AmazonServiceException ase) {
            System.err.println("Failed to fetch data from DynamoDB; " + ase.getMessage());
        } catch (AmazonClientException ace) {
            System.err.println("Failed to fetch data from DynamoDB; " + ace.getMessage());
        }
        return data;
    }

    public List<double[]> fetchDataImageproc(String tableName) {
        List<double[]> data = new ArrayList<>();
        try {
            ScanRequest scanRequest = new ScanRequest()
                .withTableName(tableName);

            ScanResult result = dynamoDB.scan(scanRequest);
            // Parameters: imagesize
            for (java.util.Map<String, AttributeValue> item : result.getItems()) {
                double[] entry = new double[2];
                entry[0] = Double.parseDouble(item.get("imageSize").getN());
                entry[1] = Double.parseDouble(item.get("workload").getN());
                data.add(entry);
            }
        } catch (AmazonServiceException ase) {
            System.err.println("Failed to fetch data from DynamoDB; " + ase.getMessage());
        } catch (AmazonClientException ace) {
            System.err.println("Failed to fetch data from DynamoDB; " + ace.getMessage());
        }
        return data;
    }
}
