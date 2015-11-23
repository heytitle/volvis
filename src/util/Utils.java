package util;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author heytitle
 */
public class Utils {
    public static int VERBOSE = 1;
    
    public static void print(String s){
       if(VERBOSE == 1){
           System.out.println(s);
       }
    }
    
    public static double findMax( double[] v ) {
        double max = 0;
        for( int i = 0 ; i < v.length; i++ ) {
            if( v[i] > max ) {
                max = v[i];
            }
        }
        return max;
    }
    
}
