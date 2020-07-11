import sim.engine.*;
import sim.util.*;
import sim.field.continuous.*;

import java.util.Comparator;
import java.util.HashMap;
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
	public static final int N_JOBS = 1;
	public static final int N_AGENTS = 10_000;
	public static       int N_NEIGHBORHOODS = 17;
	public static final int MAX_CITY_POP = 7_500;
	public static final int CITY_MOVE_INTERVAL = 100;
	public static final double CITY_DT = 1;
	public static final int STEP_LIM = 2_000_000;
	public static final int SAVE_INTERVAL = 500;
	public static final int WIDTH = 800;

	// Simulation mode
	enum Mode{
		STATIC,
		STATIC_LOW_CONNECTIVITY,
		STATIC_HIGH_CONNECTIVITY,
		CITY,
		CITY_QUARANTINE,
		CITY_SOCIAL_DISTANCING,
		CITY_QUARANTINE_MARKET,
		CITY_MASKS
	}
	public static Mode MODE = Mode.STATIC;

	// Fields
	public HashMap<Integer, Continuous2D> universe = new HashMap<>();
	private int totalConnectivity = 0; // Tot number of neighbors
	private Bag allAgents = new Bag();

	// Database
	// Expects data in the following format:
	// time, numberSick, x0, x1, ..., xN
	// where xi is the ith agent's health
	private static LinkedList<double[]> data = new LinkedList<double[]>();

	// Constructor
	public Model(long seed) {
		super(seed);

		ModelID = MODE.ordinal();

		switch(MODE){
			case STATIC:
			// Nobody moves, only one space
				N_NEIGHBORHOODS = 1;
				Agent.NEIGH_RADIUS = 50;
				break;
			case STATIC_LOW_CONNECTIVITY:
			// Nobody moves, only one space
			// Reduces neighbor radius of every agent
				N_NEIGHBORHOODS = 1;
				Agent.NEIGH_RADIUS = 10;
				break;
			case STATIC_HIGH_CONNECTIVITY:
			// Nobody moves, only one space
			// Increases neighbor radius of every agent
				N_NEIGHBORHOODS = 1;
				Agent.NEIGH_RADIUS = 200;
				break;
			case CITY:
			// Many neighborhoods which behave like STATIC
			// People from different neighborhoods periodically go to the city
			// where they move randomly
				break;
			case CITY_MASKS:
			// As before, but reducing neighbor radius of every agent
				Agent.NEIGH_RADIUS /= 3;
				break;
			case CITY_QUARANTINE:
			// Nobody is allowed to go into the city at all
				break;
			case CITY_QUARANTINE_MARKET:
			// Makes city a smaller size and reduces # of people who go there
				break;
			case CITY_SOCIAL_DISTANCING:
			// Movement algorithm avoids collisions between agents
				break;
			default:
				break;	
		}
	}

	private void clear_all() {
		super.start();
		Agent.clear();
		universe.clear();
		allAgents.clear();

		for(int i = 0; i < N_NEIGHBORHOODS; i++){
			if(MODE == Mode.CITY_QUARANTINE_MARKET && i == 0){
				// Market is a very small space
				universe.put(i, new Continuous2D(1.0, WIDTH/50, WIDTH/50));
				continue;
			}
			universe.put(i, new Continuous2D(1.0, WIDTH, WIDTH));
		}
		data.clear();
		totalConnectivity = 0;
	}

	@Override
	public void start() {
		clear_all();
		long t1 = System.currentTimeMillis();
		System.out.println("Building network...");

		// Generate agents
		for (int i = 0; i < N_AGENTS; i++) {
			int i_neighborhood;
			// Neighborhood #0 is the city; no one lives there
			if(N_NEIGHBORHOODS > 1){
				i_neighborhood = i%(N_NEIGHBORHOODS - 1) + 1;
			}else{
				i_neighborhood = 0;
			}
			Continuous2D neighborhood = universe.get(i_neighborhood);

			Agent agent = new Agent(i_neighborhood, neighborhood);
			agent.updateNeighbors(this);
			allAgents.add(agent);

			// Assign coughing events
			schedule.scheduleOnce(random.nextDouble() * agent.getCoughInterval(), agent);

			// Assign city-going events
			if(MODE == Mode.CITY || MODE == Mode.CITY_MASKS || 
			   MODE == Mode.CITY_QUARANTINE_MARKET || MODE == Mode.CITY_SOCIAL_DISTANCING){
				schedule.scheduleRepeating(new CityMover(agent), CITY_MOVE_INTERVAL);
			   }

		}
		// Log city move
		schedule.scheduleRepeating(new Steppable(){
			public void step(SimState state){
				int s = ((Model)state).universe.get(0).size();
				System.out.printf("City Pop: %d\n", s);
			}
		}, CITY_MOVE_INTERVAL);

		// Log average connectivity
		System.out.printf("Network built in %3.2f s. Average connectivity: %02.2f\n", 
						  (double) ((System.currentTimeMillis() - t1) / 1000),
						  getAvgNeighbors());

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

		// Get argument
		if(args.length > 0){
			Model.MODE = Mode.valueOf(args[0]);
			System.out.println("==============================");
			System.out.printf("Set MODE = %s\n", Model.MODE);
		}		

		// Generate model
		Model state = new Model(System.currentTimeMillis());

		// Run simulation
		for (int job = 0; job < N_JOBS; job++) {
			System.out.printf("Starting job %d\n", job);

			state.setJob(job);
			state.start();

			// Log connectivity
			BufferedWriter br = state.open_file(String.format("log_%d.dat", ModelID));
			state.write(br, Double.toString(state.getAvgNeighbors())); state.write(br, "\n");
			state.close_file(br);

			long t1 = System.currentTimeMillis();
			System.out.println("Simulating...");
			do {
				// Main loop
				if (!state.schedule.step(state))
					break;

				// Save data
				if (state.schedule.getSteps() % SAVE_INTERVAL == 0) {
					state.snapshot();
				}

				/* System.out.println(Agent.sickest.getHealth());
				System.out.println(Agent.healthiest.getHealth());
				System.out.println(); */

/* 				if(MODE == Mode.STATIC || MODE == Mode.STATIC_HIGH_CONNECTIVITY || MODE == Mode.STATIC_LOW_CONNECTIVITY){
 */				// Stop simulation if all sick, all healthy, or stagnation
				// Threshold of 1000 for #Sick is totally arbitrary,
				// it should avoid stopping when only one person is sick
					if (   Agent.sickest.getHealth()    < 1
						|| Agent.healthiest.getHealth() > 99
						|| (Agent.numberSick > 1000 && Agent.getNumberSickAvg() == Agent.numberSick)){

						System.out.print("Ending prematurely: ");
						if (Agent.sickest.getHealth() < 1) {
							System.out.print("All healthy\n");
						} else if (Agent.healthiest.getHealth() > 99){
							System.out.print("All sick\n");
						} else {
							System.out.print("Stagnation\n");
						}
						Agent.sickest.updateHealth(state.schedule.getTime());
						Agent.healthiest.updateHealth(state.schedule.getTime());
						state.kill();
						state.snapshot();
					}
				//}

			// Run for STEP_LIM steps
			} while (state.schedule.getSteps() < STEP_LIM);

			br = state.open_file(String.format("data_%d/%d.csv", ModelID, job));
			state.write_data(br);
			state.close_file(br);
			state.finish();
			System.out.printf("Simulation finished in  %3.2f s\n",
					(double) ((System.currentTimeMillis() - t1) / 1000));
			System.out.printf("Job %d done\n\n", job);
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
		for (Object a : allAgents) {
			Agent ag = (Agent) a;
			ag.updateHealth(this.schedule.getTime());
			tmp[i++] = Double.valueOf(ag.getHealth());
		}
		data.add(tmp);

		// Update average numberSick
		Agent.numberSick_window.add(Agent.numberSick);
	}

	private int[] get_save_indices() {
		// Get the indices of the 20 most sick over their lifetime
		int[][] tmp0 = new int[N_AGENTS][2];
		int i = 0;
		for (Object a : allAgents) {
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
				if(allAgents.get(i) == Agent.sickest) i_sickest = i;
				if(allAgents.get(i) == Agent.healthiest) i_healthiest = i;
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
