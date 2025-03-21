package pt.ulisboa.tecnico.cnv.loadbalancer;

import weka.classifiers.Classifier;
import weka.classifiers.functions.LinearRegression;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Attribute;
import weka.core.SerializationHelper;
import weka.core.Instance;

import java.util.ArrayList;
import java.util.List;


public class Model implements Runnable {

    private String raytracerModelPath = "raytracer.model";
    private String imageprocModelPath = "imageproc.model";
    private String modelPaths[] = {raytracerModelPath, imageprocModelPath};
    private static Instances raytracerDataset;
    private static Instances imageprocDataset;
    private static Classifier raytracerModel = null;
    private static Classifier imageprocModel = null;

    private static final Object lock = new Object();

    private static int trainingInterval = 1000 * 60 * 5;

    public Model() {
    }

    public void LoadRaytracerDataset(DynamoDBClient dbClient) {
        List<double[]> data = dbClient.fetchDataRaytracer("RaytracerMetrics");
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("scols"));
        attributes.add(new Attribute("srows"));
        attributes.add(new Attribute("wcols"));
        attributes.add(new Attribute("wrows"));
        attributes.add(new Attribute("coff"));
        attributes.add(new Attribute("roff"));
        attributes.add(new Attribute("imageSize"));
        attributes.add(new Attribute("texmapSize"));
        attributes.add(new Attribute("workload"));

        raytracerDataset = new Instances("dataset", attributes, data.size());
        raytracerDataset.setClassIndex(raytracerDataset.numAttributes() - 1);
        for (double[] instanceData : data) {
            DenseInstance instance = new DenseInstance(1.0, instanceData);
            raytracerDataset.add(instance);
        }
    }

    public void LoadImageprocDataset(DynamoDBClient dbClient) {
        List<double[]> data = dbClient.fetchDataImageproc("ImageprocMetrics");
        ArrayList<Attribute> attributes = new ArrayList<>();
        // Parameters: IMAGE SIZE, FORMAT
        attributes.add(new Attribute("imageSize"));
        attributes.add(new Attribute("workload"));
        imageprocDataset = new Instances("dataset", attributes, data.size());
        imageprocDataset.setClassIndex(imageprocDataset.numAttributes() - 1);
        for (double[] instanceData : data) {
            DenseInstance instance = new DenseInstance(1.0, instanceData);
            imageprocDataset.add(instance);
        }
        System.out.println("Imageproc dataset size: " + imageprocDataset.size());
        
    }

    public void trainModel() {
        DynamoDBClient dbClient = new DynamoDBClient();
        LoadRaytracerDataset(dbClient);
        LoadImageprocDataset(dbClient);
        for (String modelPath : modelPaths) {
            Instances dataset = null;
            if (modelPath.equals(raytracerModelPath)) {
                dataset = raytracerDataset;
            } else if (modelPath.equals(imageprocModelPath)) {
                dataset = imageprocDataset;
            }
            synchronized (lock) {
                Classifier newModel = new LinearRegression();
                try {
                    if (dataset.size() == 0) {
                        continue;
                    }
                    newModel.buildClassifier(dataset);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (modelPath.equals(raytracerModelPath))
                raytracerModel = newModel;
                else if (modelPath.equals(imageprocModelPath))
                imageprocModel = newModel;
                try {
                    SerializationHelper.write(modelPath, newModel);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            System.out.println("Training model...");
            trainModel();
            System.out.println("Model trained");
            try {
                Thread.sleep(trainingInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public double predict(double[] parameters, String requestType) {
        String modelPath = null;
        Instances dataset = null;
        Classifier model = null;
        if (requestType.equals("raytracer")) {
            modelPath = raytracerModelPath;
            dataset = raytracerDataset;
            model = raytracerModel;
        } else if (requestType.equals("imageproc")) {
            modelPath = imageprocModelPath;
            dataset = imageprocDataset;
            model = imageprocModel;
        }

        // Check if file exists
        if (!java.nio.file.Files.exists(java.nio.file.Paths.get(modelPath))) {
            return 0;
        }
        if (model == null) {
            System.out.println("Model is null");
            try {
                model = (Classifier) SerializationHelper.read(modelPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (dataset == null || dataset.size() == 0) {
            System.out.println("Dataset size: " + dataset.size());
            return -1;
        }
        synchronized (lock) {
            Instance instance = new DenseInstance(1.0, parameters);
            dataset.add(instance);
            
            try {
                double prediction = model.classifyInstance(dataset.lastInstance());
                return prediction;
            } catch (Exception e) {
                e.printStackTrace();
            }
            // remove last instance
            dataset.remove(dataset.size() - 1);
        }

        return -1;
    }
}