package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class LoadBalancerHandler implements HttpHandler {

    private static final String AWS_REGION = "eu-central-1";

    private static final double LAMBDA_THRESHOLD = 2E8;
    private static final double LAMBDA_REQUEST_THRESHOLD = 1E8;
    public static AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
    public static AWSLambda lambda = AWSLambdaClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();


    public static int counter = 0;
    public static ConcurrentHashMap<Instance, Long> currentInstances = new ConcurrentHashMap<>();
    public static List<Instance> activeInstances = new ArrayList<>();
    public static Model model = new Model();

    private static Instance getLowestLoadInstance() {
        long min = Long.MAX_VALUE;
        Instance instance = null;
        for (Instance i : activeInstances) {
            if (currentInstances.get(i) < min) {
                min = currentInstances.get(i);
                instance = i;
            }
        }
        
        return instance;
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }
        
        System.out.println("Request type: " + he.getRequestURI().getPath() + "\n");

        InputStream stream = he.getRequestBody();
        String requestBody = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        // Rebuild stream from requestBody
        InputStream reverseStream = new ByteArrayInputStream(requestBody.getBytes());
        URI requestedUri = he.getRequestURI();
        String query = requestedUri.getRawQuery();

        RequestParameters params = getParameters(query, requestBody, reverseStream);
        System.out.println(params);

        HttpResponse httpResponse = null;
        String response = "";
        boolean retry = false;
        
        do {
            retry = false;
            // get lowest load instance
            Instance instance = getLowestLoadInstance();
            System.out.println("Instance ID: " + instance.getInstanceId() + " Workload: " + currentInstances.get(instance));
            double predictedWorkload = 0;

            // Predict load
            double[] parameters = getParametersForModel(params);
            predictedWorkload = predictLoad(parameters);
            if (predictedWorkload < 0) {
                System.out.println("Failed to predict workload");
                predictedWorkload = 0;
            }
            System.out.println("Predicted load: " + predictedWorkload);
            
            if (currentInstances.get(instance) > LAMBDA_THRESHOLD && predictedWorkload < LAMBDA_REQUEST_THRESHOLD) {
                // Invoke lambda
                try {
                    response = invokeLambda(requestedUri.getPath(), params);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    retry = true;
                }
                
            } else {
                currentInstances.put(instance, currentInstances.get(instance) + (long) predictedWorkload);
                try {
                    httpResponse = post(he.getRequestURI().getPath(), query, requestBody, instance);
                    response = (String) httpResponse.body();

                    // If not successful, retry
                    if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300){
                        retry = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Remove load from instance
                currentInstances.put(instance, currentInstances.get(instance) - (long) predictedWorkload);
            }

            // Break if successful
            if (!retry) {
                break;
            }

            // Wait 1 second before retrying
            try {
                Thread.sleep(1000);
                System.out.println("Retrying...\n");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
        } while(true);
            
        he.sendResponseHeaders(200, 0);
        OutputStream os = he.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }


    public HttpResponse post(String uri, String params, String requestBody, Instance instance) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String ip = instance.getPublicIpAddress();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + ip + ":" + "8080" + uri + "?" + params))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
            
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    

    public abstract RequestParameters getParameters(String requestQuery, String requestBody, InputStream stream);
    public abstract double[] getParametersForModel(RequestParameters params);
    public abstract double predictLoad(double[] parameters);
    public abstract String invokeLambda(String requestURI, RequestParameters params);
}
