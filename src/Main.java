import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

public class Main {
    public static final int timescale = 1;

    public static final int[] temporalprobability = {   1, 1, 1, 1, 1, 2, 
                                                        8, 20, 10, 2, 1, 1,
                                                        1, 1, 1, 1, 1, 2, 
                                                        4, 8, 10, 9, 7, 5}; 

    public static void main(String[] args) {

        Timer CarGenerator = new Timer();
        GasColumn a = new GasColumn(10), b = new GasColumn(10);
        GasStation station = new GasStation(a, b);
        
        CarGenerator.scheduleAtFixedRate(new CarTask(station), 0, timescale); //Timescale is timescale ms in real for 1min in simulation
    }
}

class Car{
    private double tank;
    private double fullness;
    private double request;
    private Random gen = new Random();
    public WaitLimitTask waitTask = null;
    
    public Car(double tank, double fullness, double request){
        this.tank = tank;
        this.fullness = fullness;
        this.request = request;
        if(this.request <= this.tank*(this.fullness - 1)) throw new RuntimeException("Incorrect requested volume");
    }

    public Car(){
        tank = 10 + gen.nextInt(1900)/10;
        fullness = (10 + gen.nextInt(990))/1000;
        double restVol = tank*(1 - fullness);
        request = restVol * (1 +  gen.nextInt(99))/100;
    }

    public void setWaitLimitTask(WaitLimitTask t){
        this.waitTask = t;
    }

    double getRequest(){
        return request;
    }
}

class GasColumn{
    static int count = 1;
    public String name;
    private double perf = 20; // performance is liters per minute
    
    public GasColumn(double perf){
        this.perf = perf;
        name = "" + count;
        ++count;
    }

    public GasColumn(double perf, String name){
        this.perf = perf;
        this.name = name;        
    }
    
    public GasColumn(){
        name = "" + count;
        ++count;
    };

    public double getPerformance(){
        return perf;
    }
}

class GasStation{
    private ArrayList<GasColumn> Columns = new ArrayList<GasColumn>();
    private ArrayList<GasColumn> freecols = new ArrayList<GasColumn>();
    public ArrayList<Car> Queue = new ArrayList<Car>();
    public Timer timer = new Timer();
    public boolean buildingInProcess = false;

    public GasStation(GasColumn ... columns){
        for(int i = 0; i < columns.length; ++i){
            Columns.add(columns[i]);
        }

        for(GasColumn g : Columns){
            freecols.add(g);
        }

        System.out.println("Initialized Gas Station");
    }

    public void addCol(GasColumn c){
        Columns.add(c);
        freecols.add(c);
    }

    public boolean isFree(){
        return freecols.size() != 0;
    }

    public GasColumn allocCol(){
        return freecols.remove(0);
    }

    public void freeCol(GasColumn c){
        if(Columns.indexOf(c) == -1) throw new RuntimeException("Unknown returned column");
        else{
            freecols.add(c);
        }
    }

    public void addQueue(Car c){
        Queue.add(c);
    }

    public Car getNext(){
        return Queue.remove(0);
    }

    public int queueSize(){
        return Queue.size();
    }
}

class ColumnTask extends TimerTask{
    private GasColumn column;
    private GasStation station;
    private Timer timer;

    public ColumnTask(GasStation station, GasColumn column){
        super();
        this.station = station;
        this.column = column;
        this.timer = station.timer;
        System.out.println("\tColumn #" + column.name + " allocated");
    }

    @Override
    public void run(){
        System.out.println("\tColumn #" + column.name + " free");
        if(station.queueSize() > 0){
            System.out.println("\t\tTake next from queue, queue size: " + station.Queue.size());
            Car next = station.getNext();
            if (next.waitTask != null) next.waitTask.cancel();
            int time = (int) (next.getRequest() * Main.timescale / column.getPerformance());
            timer.schedule(new ColumnTask(station, column), time);
        }
        else{
            station.freeCol(column);
        }
    }
}

class WaitLimitTask extends TimerTask{
    private GasStation station;
    private Timer timer;
    private Car car;

    WaitLimitTask(GasStation station, Car car){
        this.station = station;
        this.timer = station.timer;
        this.car = car;
    }

    @Override
    public void run() {
        car.waitTask = null;
        if(!station.buildingInProcess){
            System.out.println("Wait limit exceeded, building new column");
            timer.schedule(new ColumnBuildTask(station), 24*2*60*Main.timescale);
            station.buildingInProcess = true;
        }
    }
}

class ColumnBuildTask extends TimerTask{
    private GasStation station;
    private Timer timer;

    ColumnBuildTask(GasStation station){
        this.station = station;
        this.timer = station.timer;
    }

    @Override
    public void run() {
        station.addCol(new GasColumn());
        station.buildingInProcess = false;
        System.out.println("\tNew column started working!");
        if(station.queueSize() > 0){
            System.out.println("\t\tTake next from queue, queue size: " + station.queueSize());
            Car next = station.getNext();
            if (next.waitTask != null) next.waitTask.cancel();
            GasColumn column = station.allocCol();
            int time = (int) (next.getRequest() * Main.timescale / column.getPerformance());
            timer.schedule(new ColumnTask(station, column), time);
        }
    }
}

class CarTask extends TimerTask{
    private Timer timer;
    private GasStation station;
    private int time = 0;
    Random gen = new Random();
    
    CarTask(GasStation station){
        super();
        this.timer = station.timer;
        this.station = station;
    }

    @Override
    public void run() {
        time = (time + 1) % (24 * 60) ;
        if(gen.nextInt(40) <= Main.temporalprobability[(int) time/60]){
            Car newCar = new Car();
            System.out.println((int) time / 60 + ":" + time % 60);
            System.out.println("\t\tNew car arrived, requested " + newCar.getRequest() + " liters");
            if(station.isFree()){
                System.out.println("\t\tFree column found");
                GasColumn col = station.allocCol();
                timer.schedule(new ColumnTask(station, col),(int) (newCar.getRequest() * Main.timescale / col.getPerformance()));
            }
            else{
                System.out.println("\t\tNo free column, pushed to queue");
                WaitLimitTask t = new WaitLimitTask(station, newCar);
                newCar.waitTask = t;
                timer.schedule(t, 12 * Main.timescale);
                station.addQueue(newCar);
            }
        }
    }
}