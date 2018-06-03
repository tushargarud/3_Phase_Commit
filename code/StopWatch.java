/*
 * Name: Tushar Garud
 * UTA Id: 1001420891
 */

import java.util.concurrent.TimeUnit;

/*
 * This class is an implementation of a stopwatch.
 * It can start and reset time counting
 */
public class StopWatch {
    private long start;
    
    //Create a new StopWatch object and save the start time
    public StopWatch() {
        start = System.currentTimeMillis();
    } 
    
    //Find elapsed time as current time - start time
    public String getElapsedTime() {
    	//Get elapsed time in milliseconds
    	long milliSecs = System.currentTimeMillis() - start;
    	
    	//Convert to seconds
    	long secs = milliSecs/1000;
    	
    	//Calculate seconds
    	long justSecs = secs % 60;
    	
    	//Calculate minutes
    	long restSecs = secs - justSecs;
    	long minutes = TimeUnit.SECONDS.toMinutes(restSecs);
    	    	
    	//Return the timestamp
    	String result = "(" + String.format("%02d", minutes) + ":" + String.format("%02d", justSecs) + ")";
    	return result;

    }
    
    public long getElapsedInSecs() {
    	//Get elapsed time in milliseconds
    	long milliSecs = System.currentTimeMillis() - start;
    	
    	//Convert to seconds
    	long secs = milliSecs/1000;
    	
    	return secs;
    }
    
    //Reset the stop watch
    public void reset() {
    	start = System.currentTimeMillis();
    }
    
}
