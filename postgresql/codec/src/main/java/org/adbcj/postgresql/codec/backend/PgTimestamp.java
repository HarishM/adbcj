package org.adbcj.postgresql.codec.backend;

import java.sql.Timestamp;
import java.util.StringTokenizer;

public class PgTimestamp {

	private String zoneTime = "";
	private Timestamp timestamp = null;
	private StringTokenizer token = null;
	
    public void setTimestamp(String s) {
    	token = new StringTokenizer(s, "+");
    	if(token.countTokens() == 1){
    		 timestamp = Timestamp.valueOf(s);
    	}
    	else if(token.countTokens() == 2){
    		String str = token.nextToken();
    		zoneTime = token.nextToken();
    		timestamp = Timestamp.valueOf(str);
    	}else
    		timestamp = null;
    }
    
    public String getTimezone(){
    	return zoneTime;
    }
    
    public Timestamp getTimestamp(){
    	return timestamp;
    }
    
    public String toString () {
    	String str = timestamp.toString();
    	if(token.countTokens() == 1)
    		return(str);
    	else
    		return(str += "+"+zoneTime);
    }
}
