import org.apache.commons.collections4.queue.CircularFifoQueue;
import ec.util.MersenneTwisterFast;
import sim.engine.*;
import sim.field.continuous.Continuous2D;
import sim.util.*;
import sim.util.distribution.Normal;

public class Agent implements Steppable {
	private static final long serialVersionUID = 1L;

    // Parameters
    public static double COUGH_INTERVAL     = 10.0;
    public static double COUGH_INTERVAL_STD = 5.0;
    public static double HEAL_RATE          = 0.2;
    public static double HEAL_RATE_STD      = 0.1;
    public static double NEIGH_RADIUS       = 200;
    public static double MAX_HEALTH         = 100;
    public static double MOVEMENT_RADIUS    = 1;

    // Distribution generators
    private static final MersenneTwisterFast EC  = new MersenneTwisterFast();
    private static final Normal coughIntervalGen = new Normal(COUGH_INTERVAL, COUGH_INTERVAL_STD, EC);
    private static final Normal healRateGen      = new Normal(HEAL_RATE, HEAL_RATE, EC);
    private static final Normal initialHealthGen = new Normal(0.0, 5.0, EC);
    
    // Keeping track of global statistics
    public static Agent sickest;
    public static Agent healthiest;
    public static int numberSick = 0;
    public static CircularFifoQueue<Integer> numberSick_window = new CircularFifoQueue<Integer>(50);

    // Fields
    public boolean isInCity = false;
    public int i_neighborhood;
    public Continuous2D neighborhood; // Reference to model's neighborhood
    private double health;
    private double coughInterval;
    private double healRate;
    private double lastUpdateTime;
    private boolean isSick;
    public Bag neighbors =  new Bag();

    public Double2D location; // Constant, within neighborhood
    public Double2D position; // Variable. within the city

    // Utils
    private double sumHealth;        // Sums lifetime health to get sickest overall agents
    private boolean doUpdate = true; // Whether should update neighbors
    public int N_notZero = 0;        // For sorting

    public static void clear(){
        numberSick = 0;
        numberSick_window.clear();
        healthiest = null;
        sickest = null;
    }

    // Constructor
    public Agent(int i_neighborhood, Continuous2D neighborhood) {
        setHealth(       Math.abs(initialHealthGen.nextDouble()));
        setCoughInterval(Math.abs(coughIntervalGen.nextDouble()));
        setHealRate(     Math.abs(healRateGen     .nextDouble()));

        this.i_neighborhood = i_neighborhood;
        this.neighborhood = neighborhood;

        this.location = newDouble2D(Model.WIDTH);
        this.position = new Double2D(this.location.x, this.location.y);
                    
        this.neighborhood.setObjectLocation(this, this.location);

        // Assign initial sickest and healthiest
        if(sickest == null){
            sickest = this;
        }else if (healthiest == null){
            healthiest = this;
            this.updateExtremes();
        }else{
            this.updateExtremes();        
        }
    }

    // Step method
    public void step(SimState state) {
        Model model = (Model) state;

        if(this.doUpdate){
            this.updateNeighbors(model);
        }

        // Cough on neighbors
        for (Object a : this.neighbors.toArray()) {
            Agent other = (Agent) a;
            if (other == this)
                continue;
            other.updateHealth(model.schedule.getTime());

            // Calculate new health
            double dist = this.position.distance(other.position);
            double new_health = other.getHealth() + this.getHealth() / dist;

            other.setHealth(new_health);
        }

        // Schedule next cough
        model.schedule.scheduleOnceIn(Math.abs(coughIntervalGen.nextDouble()), this);
    }

    public void updateHealth(double curTime) {
        double timeDiff = curTime - this.lastUpdateTime;

        this.setHealth(this.getHealth() - timeDiff * HEAL_RATE);
        this.lastUpdateTime = curTime;

        if(this.getHealth() > 0.0){
            this.N_notZero++;
        }

        this.updateExtremes();
    }

    public void updateExtremes(){
        if(this.getHealth() > sickest.getHealth()){
            sickest = this;
        }
        if (this.getHealth() < healthiest.getHealth()){
            healthiest = this;
        }
    }

    public void updateNeighbors(Model model){
        Bag new_neighbors = this.neighborhood.getNeighborsWithinDistance(
            this.neighborhood.getObjectLocation(this), NEIGH_RADIUS);

        // Add self to new neighbors's neighbor list
        for(Object a: this.neighbors){
            if(!new_neighbors.contains((Agent) a)){
                ((Agent) a).neighbors.add(this);
            }
        }

        // Update model's connectivity measure
        model.addConnectivity(-this.neighbors.size());
        model.addConnectivity(new_neighbors.size());

        // Assign
        this.doUpdate = false;
        this.neighbors = new_neighbors;
    }

    // Setters/Getters

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        // Clip between extremes
        this.health = Math.max(0.0, Math.min(health, MAX_HEALTH));
        this.sumHealth += this.health;

        // Update sick status and static counter
        if(!this.isSick){
            if(this.health >= 50){
                this.isSick = true;
                Agent.numberSick++;
            }
        }else{
            if(this.health < 50){
                this.isSick = false;
                Agent.numberSick--;
            }
        }
    }

    public static double getNumberSickAvg(){
        double out = 0.0;
        for(int i: Agent.numberSick_window){
            out += (double) i;
        }
        out /= Agent.numberSick_window.size();
        return out;
    }

    public double getCoughInterval() {
        return coughInterval;
    }

    public void setCoughInterval(double coughInterval) {
        this.coughInterval = coughInterval;
    }

    public double getHealRate() {
        return healRate;
    }

    public void setHealRate(double healRate) {
        this.healRate = healRate;
    }

    public double getSumHealth(){
        return sumHealth;
    }

    public static Double2D newDouble2D(double w){
        return new Double2D(
                    w * (EC.nextDouble() - 0.5),
                    w * (EC.nextDouble() - 0.5)
                    );
    }

}

class CityMover implements Steppable{
    private static final long serialVersionUID = 1L;

    private Agent agent;
    private Stoppable stopper = null;

    CityMover(Agent a){
        agent = a;
    }

    public void step(SimState state){
        Model m = (Model) state;
        Continuous2D city = m.universe.get(0);
        Continuous2D neigh = m.universe.get(agent.i_neighborhood);

        double prob = 0.7;
        if(Model.MODE == Model.Mode.CITY_QUARANTINE_MARKET){
            prob = 0.2;
        }

        if(!agent.isInCity && city.size() < Model.MAX_CITY_POP){
            if(m.random.nextDouble() > 1 - prob){
                agent.isInCity = true;                
                neigh.remove(agent);            
                city.setObjectLocation(agent, Agent.newDouble2D(city.getWidth()));
                
                stopper = m.schedule.scheduleRepeating(new RandomWalk(agent), Model.CITY_DT);
            }
        }else if(m.random.nextDouble() > prob){
            agent.isInCity = false;
            city.remove(agent);
            neigh.setObjectLocation(agent, agent.location);
            agent.position = agent.location;

            if(stopper == null){
                System.out.println("CRITICAL: AGENT LEFT CITY BEFORE ENTERING IT");
                System.exit(1);
            }else{
                stopper.stop();
            }
        }
    }
}

// Moves the agent inside the city
class RandomWalk implements Steppable{
    private static final long serialVersionUID = 1L;

    private Agent agent;

    public RandomWalk(Agent a){
        agent = a;
    }

    public void step(SimState state){

        if(Model.MODE == Model.Mode.CITY_SOCIAL_DISTANCING){
            // Repulsive force from neighbords
            MutableDouble2D force = new MutableDouble2D();
            MutableDouble2D tmp = new MutableDouble2D();
            for(Object a: agent.neighbors){
                Double2D pos = ((Agent) a).position;
                force.addIn(tmp.multiply(pos, -1.0/pos.length()));
            }
            force = force.resize(Agent.MOVEMENT_RADIUS/2);

            agent.position = agent.position.add(new Double2D(force));
            agent.neighborhood.setObjectLocation(agent, agent.position);
        }else{
            Double2D vector = Agent.newDouble2D(Agent.MOVEMENT_RADIUS);

            agent.position = agent.position.add(vector);
            agent.neighborhood.setObjectLocation(agent, agent.position);
        }
    }

}