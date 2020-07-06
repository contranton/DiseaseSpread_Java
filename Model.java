import sim.engine.*;
import sim.util.*;
import sim.field.continuous.*;

import java.util.Comparator;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.stream.IntStream;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class Model extends SimState {
	private static final long serialVersionUID = 1L;

	// Parameters
	public static int ModelID = 0;
	public static final int N_JOBS = 50;
	public static final int N_AGENTS = 10_000;
	public static final int STEP_LIM = 5_000_000;
	public static final int SAVE_INTERVAL = 100;
	public static final int WIDTH = 800;

	// Fields
	public Continuous2D space = new Continuous2D(1.0, WIDTH, WIDTH);
	private Bag agents = new Bag();
	private int totalConnectivity = 0; // Tot number of neighbors

	// Database
	// Expects data in the following format:
	// time, numberSick, x0, x1, ..., xN
	// where xi is the ith agent's health
	private static LinkedList<double[]> data = new LinkedList<double[]>();

	// Constructor
	public Model(long seed) {
		super(seed);
	}

	private void clear_all() {
		super.start();
		space.clear();
		Agent.clear();
		agents.clear();
		data.clear();
		totalConnectivity = 0;
	}

	@Override
	public void start() {
		clear_all();
		System.out.println("Building network...");

		// Generate agents
		for (int i = 0; i < N_AGENTS; i++) {
			Agent agent = new Agent();
			this.agents.push(agent);

			// Assign position
			space.setObjectLocation(agent, new Double2D(space.getWidth() * (random.nextDouble() - 0.5),
					space.getHeight() * (random.nextDouble() - 0.5)));

			agent.updateNeighbors(this);

			// Assign coughing events
			schedule.scheduleOnce(random.nextDouble() * agent.getCoughInterval(), agent);

		}

		// Log average connectivity
		System.out.printf("Network built. Average connectivity: %02.2f\n", getAvgNeighbors());

	}

	private BufferedWriter open_file(String path){
		return open_file(path, true);
	}

	private BufferedWriter open_file(String path, boolean delete) {
		BufferedWriter br = null;
		// File access
		File f = new File(path);
		if(delete){
			f.delete();
		}
		try {
			br = new BufferedWriter(new FileWriter(f, true));
		} catch (IOException e) {
			System.out.println(e);
			System.out.printf("ERROR: Failed to open %s. Aborting", path);
			System.exit(1);
		}
		return br;
	}

	private void close_file(BufferedWriter handle) {
		// Close file
		try {
			handle.close();
		} catch (IOException e) {
			System.out.println("ERROR: Failed to close");
		}
	}

	public static void main(String[] args) {

		// Get arguments
		if(args.length > 0){
			ModelID = Integer.parseInt(args[0]);
			System.out.printf("Set ModelID = %d", ModelID);
			if(args.length >1){
				Agent.NEIGH_RADIUS = Integer.parseInt(args[0]);
				System.out.printf("Set NEIGH_RADIUS = %d", Agent.NEIGH_RADIUS);
			}
		}		

		// Generate model
		Model state = new Model(System.currentTimeMillis());

		// Run simulation
		for (int job = 0; job < N_JOBS; job++) {
			System.out.printf("Starting job %d\n", job);
			long t1 = System.currentTimeMillis();

			state.setJob(job);
			state.start();

			// Log connectivity
			BufferedWriter br = state.open_file(String.format("log_%d.dat", ModelID));
			state.write(br, Double.toString(state.getAvgNeighbors())); state.write(br, "\n");
			state.close_file(br);

			System.out.println("Simulating...");
			do {
				// Main loop
				if (!state.schedule.step(state))
					break;

				// Save data
				if (state.schedule.getSteps() % SAVE_INTERVAL == 0) {
					state.snapshot();
				}

				// Stop simulation if all sick or all healthy
				if (Math.abs(Agent.sickest.getHealth() - Agent.healthiest.getHealth()) < 0.1) {
					System.out.print("Ending prematurely: ");
					if (Agent.sickest.getHealth() < 1) {
						System.out.print("All healthy\n");
					} else {
						System.out.print("All sick\n");
					}
					Agent.sickest.updateHealth(state);
					Agent.healthiest.updateHealth(state);
					state.kill();
					state.snapshot();
				}

				// Run for STEP_LIM steps
			} while (state.schedule.getSteps() < STEP_LIM);

			br = state.open_file(String.format("data_%d/%d.csv", ModelID, job));
			state.write_data(br);
			state.close_file(br);
			state.finish();
			System.out.printf("Finished job %d in %3.2f s\n\n", job,
					(double) ((System.currentTimeMillis() - t1) / 1000));
		}

		System.exit(0);
	}

	private void write(BufferedWriter br, String val) {
		try {
			br.write(val);
		} catch (IOException e) {
			System.out.println("FAILED TO WRITE");
		}
	}

	public void snapshot() {
		// Saves all simulation data in memory
		double[] tmp = new double[N_AGENTS + 2];
		tmp[0] = schedule.getTime();
		tmp[1] = Double.valueOf(Agent.numberSick);
		int i = 2;
		for (Object a : agents) {
			Agent ag = (Agent) a;
			ag.updateHealth(this);
			tmp[i++] = Double.valueOf(ag.getHealth());
		}
		data.add(tmp);
	}

	private int[] get_save_indices() {
		// Get the indices of the 20 most sick over their lifetime
		int[][] tmp0 = new int[N_AGENTS][2];
		int i = 0;
		for (Object a : agents) {
			// tmp0[i][0] = (int) ((Agent) a).getSumHealth();
			tmp0[i][0] = ((Agent) a).N_notZero;
			tmp0[i][1] = i++;
		}
		Arrays.sort(tmp0, Comparator.comparingDouble(s -> 1 / (1 + s[0])));
		int[] indices_sick = IntStream.range(0, 20).map(k -> tmp0[k][1]).toArray();

		// Get 100 evenly spaced indices
		int[] indices_100 = IntStream.range(0, 100).map(k -> (int) k * N_AGENTS / 100).toArray();

		// Combine
		int[] indices = Arrays.copyOf(indices_100, indices_100.length + indices_sick.length);
		System.arraycopy(indices_sick, 0, indices, indices_100.length, indices_sick.length);
		return indices;

	}

	public void write_data(BufferedWriter br) {
		// Writes simulation data into data.csv
		// Format:
		// time ; numberSick ; x0 ; x1 ; ... ; xN
		// where xi is the ith agent's health

		int[] indices = get_save_indices();
		
		///////////////////////////////////////////
		// Write data
		write(br, Integer.toString(N_AGENTS)); write(br, "\n");
		for(double[] snap: data){
			// Write current time
			write(br, Double.toString(snap[0]));
			write(br, ";");

			// Write number of sick people		
			write(br, Integer.toString((int) snap[1]));
			write(br, ";");

			// Write data for chosen agents
			for (int i : indices) {;				
				write(br, Double.toString(snap[i+2]));
				write(br, ";");
			}

			// Write data for healthiest and sickest
			int i_healthiest = 0;
			int i_sickest = 0;
			for(int i = 0; i<N_AGENTS; i++){
				if(agents.get(i) == Agent.sickest) i_sickest = i;
				if(agents.get(i) == Agent.healthiest) i_healthiest = i;
			}
			write(br, Double.toString(snap[i_healthiest+2])); write(br, ";");
			write(br, Double.toString(snap[i_sickest+2]));write(br, ";");

			write(br, "\n");
		}	
	}

	public void addConnectivity(int n) {
		totalConnectivity += n;
	}

	public double getAvgNeighbors() {
		return ((double) totalConnectivity) / ((double) N_AGENTS);
	}

}
