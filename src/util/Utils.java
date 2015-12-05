package util;

import java.util.Arrays;
import java.util.List;
import volvis.TFColor;

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
    
      public static void printVector( long[] v ) {
        String sSep = ",";
        StringBuilder sbStr = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sbStr.append(sSep);
            }
            sbStr.append(""+v[i]);
        }
        print(sbStr.toString());
    }
    public static void printVector( double[] v ) {
        String sSep = ",";
        StringBuilder sbStr = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sbStr.append(sSep);
            }
            sbStr.append(""+v[i]);
        }
        print(sbStr.toString());
    }
      public static void printVector( int[] v ) {
        String sSep = ",";
        StringBuilder sbStr = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sbStr.append(sSep);
            }
            sbStr.append(""+v[i]);
        }
        print(sbStr.toString());
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
    
    public static double dotVector( double[] a, double[] b ) {
        double sum = 0;
        for( int i = 0; i < a.length; i++ ){
            sum += ( a[i]+b[i] );
        }
        return sum;
    }
    
    public static void setTFColorFromArray( TFColor c, double[] rgb ) {
        c.r = rgb[0];
        c.g = rgb[1];
        c.b = rgb[2];
    }
}
