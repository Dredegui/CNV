package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;

/**
 *
 * @author nunes
 */
public class ImageProcHandler extends LoadBalancerHandler {

    @Override
    public RequestParameters getParameters(String query, String requestBody, InputStream stream) {
        String[] resultSplits = requestBody.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];
        
        RequestParameters params = new RequestParameters();
        params.image = resultSplits[1];
        params.imageFormat = format;
        params.imageSize = Base64.getDecoder().decode(resultSplits[1]).length;

        return params;
    }

    @Override
    public double[] getParametersForModel(RequestParameters params) {
        double[] parameters = new double[1];
        parameters[0] = params.imageSize;
        return parameters;
    }

    @Override
    public double predictLoad(double[] parameters) {
        return model.predict(parameters, "imageproc");
    }

    @Override
    public String invokeLambda(String requestURI, RequestParameters params) {
        String functionName = requestURI.split("/")[1];
        System.out.println("Invoking lambda function: " + functionName);

        String payload = String.format("{\"body\":\"%s\",\"fileFormat\":\"%s\"}", params.image, params.imageFormat);

        InvokeRequest invokeRequest = new InvokeRequest().withFunctionName(functionName)
                .withPayload(payload);
        
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
        output = String.format("data:image/%s;base64,%s", params.imageFormat, output);

        return output;
    }
}
