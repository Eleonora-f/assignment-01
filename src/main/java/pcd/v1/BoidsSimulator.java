package pcd.ass01;

import java.util.*;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BoidsSimulator {

    private final BoidsModel model;
    private Optional<BoidsView> view;
    
    private static final int FRAMERATE = 50;
    private int framerate;
    private volatile boolean running = true;
    
    public BoidsSimulator(BoidsModel model) {
        this.model = model;
        view = Optional.empty();
    }

    public void attachView(BoidsView view) {
    	this.view = Optional.of(view);
    }

    public void stopSimulation() {
        running = false;
    }

    public void runSimulation() {
        List<List<Boid>> partitions = new ArrayList<>();
        ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock(true);
        //int partitionSize = (int) Math.ceil((double) boids.size() / cores);

        List<Boid> boids = model.getBoids();
        int cores = Runtime.getRuntime().availableProcessors();
        // Creazione delle barriere per sincronizzare i worker con il thread principale
        var velocityBarrier = new CyclicBarrier(cores + 1);
        var positionBarrier = new CyclicBarrier(cores + 1);

        List<BoidWorker> workers = startWorkers(partitions, velocityBarrier, positionBarrier);
        
        for (int i = 0; i < cores; i++) {
            partitions.add(boids.subList(i * (boids.size() / cores), (boids.size() / cores) * (i + 1)));
        }

        /*for(int i = 0; i < cores; i++) {
            int start = i * partitionSize;
            int end = Math.min(start + partitionSize, boids.size());
            partitions.add(new ArrayList<>(boids.subList(start, end)));
        }*/


        /*var collectDataBarrier = new CyclicBarrier(cores + 1);
        var writeDataBarrier = new CyclicBarrier(cores + 1);*/



        for (List<Boid> partition: partitions) {
            var worker = new BoidWorker(partition, model, velocityBarrier, positionBarrier);
            worker.start();
            workers.add(worker);
        }

        while (running) {
            if (model.getIsRunning()) {
            var t0 = System.currentTimeMillis();
    		//var boids = model.getBoids();
    		/*
    		for (Boid boid : boids) {
                boid.update(model);
            }
            */
    		
    		/* 
    		 * Improved correctness: first update velocities...
    		 */
    		for (Boid boid : boids) {
                boid.updateVelocity(model);
            }

    		/* 
    		 * ..then update positions
    		 */
    		for (Boid boid : boids) {
                boid.updatePos(model);
            }

                try {
                    // Aspetta che tutti i worker aggiornino le velocità
                    velocityBarrier.await();

                    // Aspetta che tutti i worker aggiornino le posizioni
                    positionBarrier.await();

                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                    break; // Esce dal loop se viene interrotto
                }

    		if (view.isPresent()) {
            	view.get().update(framerate);
            	var t1 = System.currentTimeMillis();
                var dtElapsed = t1 - t0;
                var framratePeriod = 1000/FRAMERATE;
                
                if (dtElapsed < framratePeriod) {		
                	try {
                		Thread.sleep(framratePeriod - dtElapsed);
                	} catch (Exception ex) {}
                	framerate = FRAMERATE;
                } else {
                	framerate = (int) (1000/dtElapsed);
                }
    		}
            
    	}
    }
}
