import sim.engine.*;
import sim.util.*;
import sim.field.continuous.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class Model extends SimState {

	// Parameters
	public static final int N_AGENTS      = 10000;
	public static final int STEP_LIM      = 6000;
	public static final int SAVE_INTERVAL = 10;
	public static final int WIDTH         = 800;

	// Fields
	public Continuous2D space = new Continuous2D(1.0, WIDTH, WIDTH);
	private Bag agents = new Bag();

	// File Writer
	static BufferedWriter br;

	// Constructor
	public Model(long seed) {
		super(seed);
	}

	@Override
	public void start() {
		super.start();
		space.clear();

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

	public static void main(String[] args) {

		System.out.println("Starting");
		// File access
		File f = new File("data.csv");
		f.delete();
		try {
			br = new BufferedWriter(new FileWriter(f, true));
		} catch (IOException e) {
			System.out.println("ERROR: Failed to open data.csv. Aborting");
			System.exit(0);
		}

		// Model simulation
		int jobs = 1;
		Model state = new Model(System.currentTimeMillis());

		for (int job = 0; job < jobs; job++) {
			state.setJob(job);
			state.start();
			do {
				// Main loop
				if (!state.schedule.step(state))
					break;

				// Save data
				if (state.schedule.getSteps() % SAVE_INTERVAL == 0) {
					state.write_data();
				}

				// Stop simulation if all sick or all healthy
				if (Math.abs(Agent.sickest.getHealth() - Agent.healthiest.getHealth()) < 0.01){
					System.out.print("Ending prematurely: ");
					if(Agent.sickest.getHealth() < 1){
						System.out.print("All healthy\n");
					}else{
						System.out.print("All sick\n");
					}
					state.kill();
					state.write_data();
				}

				// Run for STEP_LIM steps
			} while (state.schedule.getSteps() < STEP_LIM);

			state.finish();
		}

		// Close file
		try {
			br.close();
		} catch (IOException e) {
			System.out.println("ERROR: Failed to close data.csv");
		}

		System.out.println("Done");
		System.exit(0);
	}

	private void write(String val) {
		try {
			br.write(val);
		} catch (IOException e) {
			System.out.println("FAILED TO WRITE");
		}
	}

	public void write_data() {
		// TODO: Calculate important parameters considering all agents and write to a
		// different file

		// Write current time
		write(Double.toString(schedule.getTime()));
		write(";");

		// Write data for only every 100 agents (for viz purposes) TODO:
		int i = 0;
		for (Object tmp : this.agents) {
			i++;
			if (i % 1 != 0)
				continue;
			Agent a = (Agent) tmp;
			a.updateHealth(this);
			write(Float.toString((float) a.getHealth()));
			write(";");
		}
		write("\n");
	}


}
