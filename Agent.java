import ec.util.MersenneTwisterFast;
import sim.engine.*;
import sim.util.*;
import sim.util.distribution.Normal;

public class Agent implements Steppable {
	private static final long serialVersionUID = 1L;

    // Parameters
    public static double COUGH_INTERVAL     = 50.0;
    public static double COUGH_INTERVAL_STD = 20.0;
    public static double HEAL_RATE          = 0.2;
    public static double HEAL_RATE_STD      = 0.1;
    public static double NEIGH_RADIUS       = 50;
    public static double MAX_HEALTH         = 100;

    // Distribution generators
    private static final MersenneTwisterFast EC  = new MersenneTwisterFast();
    private static final Normal coughIntervalGen = new Normal(COUGH_INTERVAL, COUGH_INTERVAL_STD, EC);
    private static final Normal healRateGen      = new Normal(HEAL_RATE, HEAL_RATE, EC);
    private static final Normal initialHealthGen = new Normal(0.0, 5.0, EC);
    
    // Keeping track of global statistics
    public static Agent sickest;
    public static Agent healthiest;
    public static int numberSick = 0;

    // Fields
    private double health;
    private double coughInterval;
    private double healRate;
    private double lastUpdateTime;
    private boolean isSick;
    private Bag neighbors =  new Bag();

    // Utils
    private double sumHealth;        // Sums lifetime health to get sickest overall agents
    private boolean doUpdate = true; // Whether should update neighbors
    public int N_notZero = 0;        // For sorting

    public static void clear(){
        numberSick = 0;
        healthiest = null;
        sickest = null;
    }

    // Constructor
    public Agent() {
        setHealth(       Math.abs(initialHealthGen.nextDouble()));
        setCoughInterval(Math.abs(coughIntervalGen.nextDouble()));
        setHealRate(     Math.abs(healRateGen     .nextDouble()));

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
        Double2D selfLoc = model.space.getObjectLocation(this);

        if(this.doUpdate){
            this.updateNeighbors(model);
        }

        for (Object a : neighbors.toArray()) {
            Agent other = (Agent) a;
            if (other == this)
                continue;
            other.updateHealth(model);

            // Calculate new health
            Double2D otherLoc = model.space.getObjectLocation(other);
            double dist = selfLoc.distance(otherLoc);
            double new_health = other.getHealth() + this.getHealth() / dist;

            other.setHealth(new_health);
        }

        model.schedule.scheduleOnceIn(Math.abs(coughIntervalGen.nextDouble()), this);
    }

    public void updateHealth(Model model) {
        double curTime = model.schedule.getTime();
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
        Bag new_neighbors = model.space.getNeighborsWithinDistance(model.space.getObjectLocation(this), NEIGH_RADIUS);

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

}
