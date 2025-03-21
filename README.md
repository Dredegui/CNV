# SpecialVFX@Cloud

This project contains three sub-projects:

1. `raytracer` - the Ray Tracing workload
2. `imageproc` - the BlurImage and EnhanceImage workloads
3. `webserver` - the web server exposing the functionality of the workloads
4. `loadbalancer` - the load balancer that forwards requests to the worker instances

Refer to the `README.md` files of the sub-projects to get more details about each specific sub-project.

## How to build everything

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

## How to run and instrument Webserver

1. Run `./instrumentAndRunWebserver.sh`

## How to run Load Balancer and Auto-Scaler

1. cd into the `loadbalancer` directory
2. Run `java -cp target/loadbalancer-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer`

## Architecture

### Webserver

The webserver is a simple web server that exposes the functionality of the workloads. When instrumented using the JavaAssist tool we developed, the metrics of the workloads are collected and sent to a DynamoDB table.

### Load Balancer

The load balancer is a webserver that receives requests and forwards them to the worker instances. The worker instances can be either a VM Worker or a Lambda Worker.
To decide which worker it should send the request, the load balancer has a thread that trains a model with DynamoDB data to predict the workload/cost of the incoming request. Based on the prediction given by the model and the current workload of the workers, the load balancer decides whether to forward the request to a VM Worker instance or a Lambda Worker.

### Auto-Scaler

The auto-scaler is a thread that runs in the load balancer and is responsible for adding and removing virtual machines based on the CPU Utilization of the Worker instances.