/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;
import volume.Volume;
import util.VectorMath;
import java.util.List;
import java.util.ArrayList;
/**
 *
 * @author s152126
 */
public class RayCastingCubeIntersection {
   
    private int imageCenter;
    private double[] rayDirection;
    private double[] uVec;
    private double[] vVec;
    private double[] rayOrigin = new double[3];
    private Volume cube;
    private double[] cubeCenter;
   
    public RayCastingCubeIntersection(Volume cube,int imageCenter,double[] rayDirection,double[] uVec,double[] vVec,double[] cubeCenter){
        this.cube = cube;
        this.imageCenter = imageCenter;
        this.rayDirection = rayDirection;
        this.uVec = uVec;
        this.vVec = vVec;
        this.cubeCenter = cubeCenter;
    }
    
    public List<Integer> findIntersectionParameters(int i,int j){
        int maxVal = Integer.MIN_VALUE;
        int kStart = Integer.MIN_VALUE;
        int kEnd = Integer.MAX_VALUE;
        int[] k = new int[6];
        double[] intersection = new double[3];
        double[] minCubeBoundary = new double[3];
        double[] maxCubeBoundary = new double[3];
        VectorMath.setVector(minCubeBoundary,-cube.getDimX()/2,-cube.getDimY()/2,-cube.getDimZ()/2);
        VectorMath.setVector(maxCubeBoundary,cube.getDimX()/2,cube.getDimY()/2,cube.getDimZ()/2);
        VectorMath.setVector(rayOrigin,(i-imageCenter)*uVec[0] + (j-imageCenter)*vVec[0],(i-imageCenter)*uVec[1] + (j-imageCenter)*vVec[1] , (i-imageCenter)*uVec[2] + (j-imageCenter)*vVec[2]);
        k[0] = (int) ((minCubeBoundary[0] -rayOrigin[0]) / rayDirection[0]);
        k[1] = (int)((maxCubeBoundary[0] -rayOrigin[0]) / rayDirection[0]);
        k[2] = (int) ((minCubeBoundary[1] -rayOrigin[1]) / rayDirection[1]);
        k[3] = (int) ((maxCubeBoundary[1] -rayOrigin[1]) / rayDirection[1]);
        k[4] = (int) ((minCubeBoundary[2] -rayOrigin[2]) / rayDirection[2]);
        k[5] = (int) ((maxCubeBoundary[2] -rayOrigin[2]) / rayDirection[2]);
         List<Integer> kList = new ArrayList<Integer>();
        for(int m = 0 ; m<6 ; m++){
            VectorMath.setVector(intersection, rayOrigin[0]+k[m]*rayDirection[0]  , rayOrigin[1]+k[m]*rayDirection[1], rayOrigin[2]+k[m]*rayDirection[2] );
            if(intersection[0]>=minCubeBoundary[0] && intersection[0]<=maxCubeBoundary[0] && intersection[1]>=minCubeBoundary[1] && intersection[1]<=maxCubeBoundary[1] && intersection[2]>=minCubeBoundary[2] && intersection[2]<=maxCubeBoundary[2] ){
               kList.add(k[m]);
            }
        }
       // System.out.println("kcount = "+kList.size());
        if(kList.size()!=2 && kList.size()!=1) return null;
        //System.out.println(kList.get(0)+"::"+kList.get(1));
        return kList;
    }
   
}     
