package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

public class LoadBalancer {

    public static void main(String[] args) throws Exception {
        // Run model training on a separate thread
        Model model = new Model();
        Thread modelThread = new Thread(model);
        modelThread.start();
        // Run auto scaler on a separate thread
        AutoScaler autoScaler = new AutoScaler();
        Thread autoScalerThread = new Thread(autoScaler);
        autoScalerThread.start();
        // Run load balancer
        HttpServer server = HttpServer.create(new InetSocketAddress(8040), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/blurimage", new ImageProcHandler());
        server.createContext("/enhanceimage", new ImageProcHandler());
        server.createContext("/raytracer", new RaytracerHandler());
        server.start();
    }
}
