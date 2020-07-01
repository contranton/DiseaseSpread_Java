import ec.util.MersenneTwisterFast;
import sim.engine.*;
import sim.util.*;
import sim.util.distribution.Normal;

public class Agent implements Steppable {

    // Parameters
    public static final double COUGH_INTERVAL     = 200.0;
    public static final double COUGH_INTERVAL_STD = 200.0;
    public static final double HEAL_RATE          = 0.4;
    public static final double HEAL_RATE_STD      = 0.2;
    public static final double NEIGH_RADIUS       = 200;
    public static final double MAX_HEALTH         = 100;

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
    private Bag neighbors;

    // Utils
    private double sumHealth; // Sums lifetime health to get sickest overall agents
    private boolean doUpdate = true; // Whether should update neighbors

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

            // Transfer function
            Double2D otherLoc = model.space.getObjectLocation(other);
            double dist = selfLoc.distance(otherLoc);
            other.setHealth(other.getHealth() + this.getHealth() / dist);
        }

        model.schedule.scheduleOnceIn(Math.abs(coughIntervalGen.nextDouble()), this);
    }

    public void updateHealth(Model model) {
        double curTime = model.schedule.getTime();
        double timeDiff = curTime - this.lastUpdateTime;

        this.setHealth(this.getHealth() - timeDiff * HEAL_RATE);
        this.lastUpdateTime = curTime;

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
        this.neighbors = model.space.getNeighborsWithinDistance(model.space.getObjectLocation(this), NEIGH_RADIUS);
        this.doUpdate = false;
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
