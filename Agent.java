import ec.util.MersenneTwisterFast;
import sim.engine.*;
import sim.util.*;
import sim.util.distribution.Normal;

public class Agent implements Steppable {

    // Parameters
    public static final double COUGH_INTERVAL     = 800.0;
    public static final double COUGH_INTERVAL_STD = 400.0;
    public static final double HEAL_RATE          = 0.3;
    public static final double HEAL_RATE_STD      = 0.1;
    public static final double NEIGH_RADIUS       = 200;
    public static final double MAX_HEALTH         = 100;

    // Distribution generators
    private static final MersenneTwisterFast EC  = new MersenneTwisterFast();
    private static final Normal coughIntervalGen = new Normal(COUGH_INTERVAL, COUGH_INTERVAL_STD, EC);
    private static final Normal healRateGen      = new Normal(HEAL_RATE, HEAL_RATE, EC);
    private static final Normal initialHealthGen = new Normal(0.0, 5.0, EC);
    
    // Keeping track of extremes
    public static Agent sickest;
    public static Agent healthiest;

    // Fields
    private double health;
    private double coughInterval;
    private double healRate;
    private double lastUpdateTime;


    // Constructor
    public Agent() {
        setHealth(       Math.abs(initialHealthGen.nextDouble()));
        setCoughInterval(Math.abs(coughIntervalGen.nextDouble()));
        setHealRate(     Math.abs(healRateGen     .nextDouble()));

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

        Bag others = model.space.getNeighborsWithinDistance(model.space.getObjectLocation(this), NEIGH_RADIUS);

        for (Object a : others.toArray()) {
            Agent other = (Agent) a;
            if (other == this)
                continue;
            other.updateHealth(model);

            // Transfer function
            Double2D otherLoc = model.space.getObjectLocation(other);
            double dist = selfLoc.distance(otherLoc);
            other.setHealth(other.getHealth() + this.getHealth() / dist);
        }
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

    // Setters/Getters

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(health, MAX_HEALTH));
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

}
