package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;

public class RaytracerHandler extends LoadBalancerHandler {

    private final static ObjectMapper mapper = new ObjectMapper();

    @Override
    public RequestParameters getParameters(String query, String requestBody, InputStream stream) {
        // Example of request: http://127.0.0.1:8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false\&multi=false
        RequestParameters params = new RequestParameters();
        Map<String, String> parameters = queryToMap(query);

        params.scols = Integer.parseInt(parameters.getOrDefault("scols", "0"));
        params.srows = Integer.parseInt(parameters.getOrDefault("srows", "0"));
        params.wcols = Integer.parseInt(parameters.getOrDefault("wcols", "0"));
        params.wrows = Integer.parseInt(parameters.getOrDefault("wrows", "0"));
        params.coff = Integer.parseInt(parameters.getOrDefault("coff", "0"));
        params.roff = Integer.parseInt(parameters.getOrDefault("roff", "0"));
        params.aa = Boolean.parseBoolean(parameters.getOrDefault("aa", "false"));
        params.multi = Boolean.parseBoolean(parameters.getOrDefault("multi", "false")); 

        try {
            Map<String, Object> body = mapper.readValue(stream, new TypeReference<>() {});
            byte[] input = ((String) body.get("scene")).getBytes();
            byte[] texmap = null;
            if (body.containsKey("texmap")) {
                // Convert ArrayList<Integer> to byte[]
                ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
                texmap = new byte[texmapBytes.size()];
                for (int i = 0; i < texmapBytes.size(); i++) {
                    texmap[i] = texmapBytes.get(i).byteValue();
                }
            }

            params.input = input;
            params.texmap = texmap;
            params.imageSize = input.length;
            params.texmapSize = texmap != null ? texmap.length : 0;
        } catch (Exception e) { 
            e.printStackTrace();
        }

        return params;
    }

    @Override
    public double[] getParametersForModel(RequestParameters params) {
        double[] parameters = new double[8];
        parameters[0] = params.scols;
        parameters[1] = params.srows;
        parameters[2] = params.wcols;
        parameters[3] = params.wrows;
        parameters[4] = params.coff;
        parameters[5] = params.roff;
        parameters[6] = params.imageSize;
        parameters[7] = params.texmapSize;
        return parameters;
    }

    @Override
    public double predictLoad(double[] parameters) {
        return model.predict(parameters, "raytracer");
    }

    public Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    @Override
    public String invokeLambda(String requestURI, RequestParameters params) {
        String functionName = requestURI.split("/")[1];
        System.out.println("Invoking LAMBDA function: " + functionName);

        String b64Input = Base64.getEncoder().encodeToString(params.input);
        String b64Texmap = params.texmap != null ? Base64.getEncoder().encodeToString(params.texmap) : "";  

        JsonObject payload = new JsonObject();
        payload.addProperty("scols", params.scols);
        payload.addProperty("srows", params.srows);
        payload.addProperty("wcols", params.wcols);
        payload.addProperty("wrows", params.wrows);
        payload.addProperty("coff", params.coff);
        payload.addProperty("roff", params.roff);
        payload.addProperty("aa", params.aa);
        payload.addProperty("multi", params.multi);
        payload.addProperty("input", b64Input);
        payload.addProperty("texmap", b64Texmap);

        String payloadString = payload.toString();

        
        InvokeRequest invokeRequest = new InvokeRequest().withFunctionName(functionName)
                .withPayload(payloadString);

        InvokeResult result = null;
        try {
            result = lambda.invoke(invokeRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Check error
        if (result == null || result.getFunctionError() != null) {
            throw new RuntimeException("Error invoking lambda function");
        }

        String output = new String(result.getPayload().array());
        output = output.substring(1, output.length() - 1);
        String response = String.format("data:image/bmp;base64,%s", output);

        return response;
    }
}
