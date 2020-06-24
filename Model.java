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

	// Parameters
	public static final int N_JOBS        = 10;
	public static final int N_AGENTS      = 10000;
	public static final int STEP_LIM      = 6000;
	public static final int SAVE_INTERVAL = 10;
	public static final int WIDTH         = 800;

	// Fields
	public Continuous2D space = new Continuous2D(1.0, WIDTH, WIDTH);
	private Bag agents = new Bag();

	// Database
	// Expects data in the following format:
	// time, numberSick, x0, x1, ..., xN
	// where xi is the ith agent's health
	private static LinkedList<double[]> data = new LinkedList<double[]>(); 

	// Constructor
	public Model(long seed) {
		super(seed);
	}

	@Override
	public void start() {
		super.start();
		space.clear();
		Agent.clear();
		agents.clear();
		data.clear();

		// Generate agents
		for (int i = 0; i < N_AGENTS; i++) {
			Agent agent = new Agent();
			this.agents.push(agent);

			// Assign position
			space.setObjectLocation(agent, new Double2D(space.getWidth() * (random.nextDouble() - 0.5),
					space.getHeight() * (random.nextDouble() - 0.5)));

			// Assign coughing events
			schedule.scheduleOnce(random.nextDouble() * agent.getCoughInterval(), agent);

		}

	}

	private BufferedWriter open_file(int id){
		BufferedWriter br = null;
		// File access
		File f = new File(String.format("data/%d.csv", id));
		f.delete();
		try {
			br = new BufferedWriter(new FileWriter(f, true));
		} catch (IOException e) {
			System.out.println(e);
			System.out.printf("ERROR: Failed to open data/%d.csv. Aborting", id);
			System.exit(0);
		}
		return br;
	}

	private void close_file(BufferedWriter handle){
		// Close file
		try {
			handle.close();
		} catch (IOException e) {
			System.out.println("ERROR: Failed to close");
		}
	}

	public static void main(String[] args) {

		// Model simulation	
		Model state = new Model(System.currentTimeMillis());

		for (int job = 0; job < N_JOBS; job++) {
			System.out.printf("Starting job %d\n", job);

			state.setJob(job);
			state.start();
			do {
				// Main loop
				if (!state.schedule.step(state))
					break;

				// Save data
				if (state.schedule.getSteps() % SAVE_INTERVAL == 0) {
					state.snapshot();
				}

				// Stop simulation if all sick or all healthy
				if (Math.abs(Agent.sickest.getHealth() - Agent.healthiest.getHealth()) < 0.1){
					System.out.print("Ending prematurely: ");
					if(Agent.sickest.getHealth() < 1){
						System.out.print("All healthy\n");
					}else{
						System.out.print("All sick\n");
					}
					state.kill();
					state.snapshot();
				}

				// Run for STEP_LIM steps
			} while (state.schedule.getSteps() < STEP_LIM);

			BufferedWriter br = state.open_file(job);
			state.write_data(br);
			state.close_file(br);
			state.finish();
			System.out.printf("Finished job %d\n", job);
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

	public void snapshot(){
		// Saves all simulation data in memory
		double[] tmp = new double[N_AGENTS+2];
		tmp[0] = schedule.getTime();
		tmp[1] = Double.valueOf(Agent.numberSick);
		int i = 2;
		for(Object a: agents){
			Agent ag = (Agent) a;
			tmp[i++] = Double.valueOf(ag.getHealth());
			ag.updateHealth(this);
		}
		data.add(tmp);
	}

	private int[] get_save_indices(){
		///////////////////////////////////////////////
		// Get the indices of the 20 most sick over their lifetime
		int[][] tmp0 = new int[N_AGENTS][2];
		int i = 0;
		for(Object a: agents){
			tmp0[i][0] = (int) ((Agent) a).getSumHealth();
			tmp0[i][1] = i++;
		}
		Arrays.sort(tmp0, Comparator.comparingDouble(s->s[0]));
		int[] indices_sick = IntStream.range(0,20).map(k->tmp0[k][1]).toArray();

		// Get 100 evenly spaced indices
		int[] indices_100 = IntStream.range(0, 100).map(k->(int)k*N_AGENTS/100).toArray();

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

			// Write data for only every 100 agents (for viz purposes) TODO:
			for (int i : indices) {;				
				write(br, Double.toString(snap[i+2]));
				write(br, ";");
			}
			write(br, "\n");
		}	
	}	

}
