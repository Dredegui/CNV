package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
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

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class ICount extends AbstractJavassistTool {

    private static AmazonDynamoDB dynamoDB;

    private static String raytracerTable = "RaytracerMetrics";
    private static String imageprocTable = "ImageprocMetrics";

    static class Parameters {
        public String toString() {
            return "No parameters";
        }
    }

    static class RayTracerParameters extends Parameters {
        public boolean aa;
        public boolean multi;
        public int scols;
        public int srows;
        public int wcols;
        public int wrows;
        public int coff;
        public int roff;
        public int imageSize;
        public int texmapSize;

        public RayTracerParameters() {}

        @Override
        public String toString() {
            return String.format("Parameters: AA %s, MULTI %s, SCOLS %s, SROWS %s, WCOLS %s, WROWS %s, COFF %s, ROFF %s, IMAGESIZE %s, TEXMAPSIZE %s,", aa, multi, scols, srows, wcols, wrows, coff, roff, imageSize, texmapSize);
        }
    }

    static class ImageProcessingParameters extends Parameters {
        public int imageSize;
        public String format;

        public ImageProcessingParameters() {}

        @Override
        public String toString() {
            return String.format("Parameters: IMAGE SIZE (bytes) %s, FORMAT %s", imageSize, format);
        }
    }

    static class Metrics {
        public long threadId;
        public long basicBlocks;
        public Parameters parameters;

        public Metrics() {}

        public Metrics(long threadId) {
            this.threadId = threadId;
            this.basicBlocks = 0;
            this.parameters = new Parameters();
        }

        @Override
        public String toString() {
            return String.format("Thread %s: Basic Blocks %s, %s", threadId, basicBlocks, parameters.toString());
        }
    }
    
    // Map Thread to Metrics
    private static Map<Long, Metrics> threadMetrics = new ConcurrentHashMap<>();

    public static void createDynamoDB() {
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withCredentials(new EnvironmentVariableCredentialsProvider())
        .withRegion("eu-central-1")
        .build();
        
        String tables[] = {raytracerTable, imageprocTable};
        for (String tableName : tables) {
            // Create a table with a primary hash key named 'name', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
            .withKeySchema(new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH))
            .withAttributeDefinitions(new AttributeDefinition().withAttributeName("id").withAttributeType(ScalarAttributeType.S))
            .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
            
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            try {
                TableUtils.waitUntilActive(dynamoDB, tableName);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // Describe our new table
            DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
            TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
            System.out.println("Table Description: " + tableDescription);
        }
    }

    static {
        createDynamoDB();
    }

    public static void saveMetricsToDB() {
        long threadId = Thread.currentThread().getId();
        Metrics metrics = threadMetrics.get(threadId);
        String id = java.util.UUID.randomUUID().toString();
        PutItemRequest putItemRequest = null;
        if (metrics.parameters.toString().contains("AA")) {
            RayTracerParameters specializedParameters = (RayTracerParameters) metrics.parameters;
            putItemRequest = new PutItemRequest(raytracerTable, Map.of(
                "id", new AttributeValue().withS(id),
                "scols", new AttributeValue().withN(String.valueOf(specializedParameters.scols)),
                "srows", new AttributeValue().withN(String.valueOf(specializedParameters.srows)),
                "wcols", new AttributeValue().withN(String.valueOf(specializedParameters.wcols)),
                "wrows", new AttributeValue().withN(String.valueOf(specializedParameters.wrows)),
                "coff", new AttributeValue().withN(String.valueOf(specializedParameters.coff)),
                "roff", new AttributeValue().withN(String.valueOf(specializedParameters.roff)),
                "imageSize", new AttributeValue().withN(String.valueOf(specializedParameters.imageSize)),
                "texmapSize", new AttributeValue().withN(String.valueOf(specializedParameters.texmapSize)),
                "workload", new AttributeValue().withN(String.valueOf(metrics.basicBlocks))
            ));
        } else {
            ImageProcessingParameters specializedParameters = (ImageProcessingParameters) metrics.parameters;
            putItemRequest = new PutItemRequest(imageprocTable, Map.of(
                "id", new AttributeValue().withS(id),
                "imageSize", new AttributeValue().withN(String.valueOf(specializedParameters.imageSize)),
                "format", new AttributeValue().withS(specializedParameters.format),
                "workload", new AttributeValue().withN(String.valueOf(metrics.basicBlocks))
            ));
        }
        putItemRequest.setReturnValues(ReturnValue.ALL_OLD);
        System.out.println("Adding a new item...");
        try {
            PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);
            System.out.println("Result: " + putItemResult);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered a serious internal problem while trying to communicate with AWS, such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    
    }

    public static void getMetricsFromDB() {
        ScanRequest scanRequest = new ScanRequest().withTableName(imageprocTable);
        ScanResult result = dynamoDB.scan(scanRequest);
        System.out.println("Getting metrics from DB...");
        System.out.println(result);
        scanRequest = new ScanRequest().withTableName(raytracerTable);
        result = dynamoDB.scan(scanRequest);
        System.out.println("Getting metrics from DB...");
        System.out.println(result);
    }

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length) {
        long threadId = Thread.currentThread().getId();
        Metrics metrics = threadMetrics.getOrDefault(threadId, new Metrics(threadId));
        metrics.basicBlocks++;
    }

    public static void getRequestParameters(Map<String, String> parameters, byte[] input, byte[]texmap) {
        long threadId = Thread.currentThread().getId();
        
        Metrics metrics = threadMetrics.get(threadId);
        metrics.parameters = new RayTracerParameters();

        RayTracerParameters params = (RayTracerParameters) metrics.parameters;
        params.aa = Boolean.parseBoolean(parameters.getOrDefault("aa", "false"));
        params.multi = Boolean.parseBoolean(parameters.getOrDefault("multi", "false"));
        params.scols = Integer.parseInt(parameters.getOrDefault("scols", "0"));
        params.srows = Integer.parseInt(parameters.getOrDefault("srows", "0"));
        params.wcols = Integer.parseInt(parameters.getOrDefault("wcols", "0"));
        params.wrows = Integer.parseInt(parameters.getOrDefault("wrows", "0"));
        params.coff = Integer.parseInt(parameters.getOrDefault("coff", "0"));
        params.roff = Integer.parseInt(parameters.getOrDefault("roff", "0"));
        params.imageSize = input.length;
        if (texmap != null) {
            params.texmapSize = texmap.length;
        } else {
            params.texmapSize = 0;
        }
    }

    public static void getRequestParameters(byte[] decoded, String format) {
        long threadId = Thread.currentThread().getId();

        Metrics metrics = threadMetrics.get(threadId);
        metrics.parameters = new ImageProcessingParameters();
        
        ImageProcessingParameters params = (ImageProcessingParameters) metrics.parameters;
        params.imageSize = decoded.length;
        params.format = format;
    }

    synchronized public static void writeToFile(String message, String path) {
        try {
            // create file if it doesn't exist
            try { java.nio.file.Files.createFile(java.nio.file.Paths.get(path));
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // file already exists - it's ok to ignore this exception
            }
            // append message to file
            java.nio.file.Files.write(java.nio.file.Paths.get(path), message.getBytes(), java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static void printMetrics() {
        long threadId = Thread.currentThread().getId();
        System.out.println(threadMetrics.get(threadId).toString());
    }

    public static void writeStatisticsToFile() {
        long threadId = Thread.currentThread().getId();
        String message = threadMetrics.get(threadId).toString() + "\n";
        ICount.writeToFile(message, "/tmp/output.txt");
    }

    public static void createMetric() {
        long threadId = Thread.currentThread().getId();
        threadMetrics.put(threadId, new Metrics(threadId));
    }

    public static void removeMetric() {
        long threadId = Thread.currentThread().getId();
        threadMetrics.remove(threadId);
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        if (behavior.getLongName().contains("pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler.handle(")) {
            behavior.insertBefore(String.format("%s.createMetric();", ICount.class.getName()));
            behavior.insertAt(65, String.format("%s.getRequestParameters(parameters, input, texmap);", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.printMetrics();", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.saveMetricsToDB();", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.removeMetric();", ICount.class.getName()));
        } else if (behavior.getLongName().contains("pt.ulisboa.tecnico.cnv.imageproc.ImageProcessingHandler.handleRequest(java.lang.String,java.lang.String)")) {
            behavior.insertBefore(String.format("%s.createMetric();", ICount.class.getName()));
            behavior.insertAt(28, String.format("%s.getRequestParameters(decoded, format);", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.printMetrics();", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.saveMetricsToDB();", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.removeMetric();", ICount.class.getName()));
        }
    }


    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        if (!(block.behavior.getLongName().contains("pt.ulisboa.tecnico.cnv.raytracer.Main") || block.behavior.getLongName().contains("pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler") || block.behavior.getLongName().contains("pt.ulisboa.tecnico.cnv.imageproc.ImageProcessingHandler"))) {
            block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(), block.getPosition(), block.getLength()));
        }
    }
}
