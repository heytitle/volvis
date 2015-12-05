
import util.Utils;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author heytitle
 */
public class NewClass {
    
    public static void main(String[] args) {
        Utils.printVector(NewClass.colorArray(0) );
        
        Utils.print(" "+NewClass.binaryColor( new int[]{0,0,1,255} ));
    }
    
     public static int[] colorArray( int c ){ 
         Utils.print(">> " + ( (c & 0xff000000) >> 24 ));
        return new int[]{ ( (c & 0xff000000) >> 24 ) , ( (c & 0x00ff0000) >> 16 ), ( (c & 0x0000ff00) >> 8 ), ( c & 0x000000ff ) };
    }
    
     public static int binaryColor( int[] rgba ){
        return ( rgba[0] << 24) | ( rgba[1] << 16) | ( rgba[2] << 8) | rgba[3];
    }
    
}
