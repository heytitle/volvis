/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

/**
 *
 * @author michel
 */
public class GradientVolume {

    public GradientVolume(Volume vol) {
        volume = vol;
        dimX = vol.getDimX();
        dimY = vol.getDimY();
        dimZ = vol.getDimZ();
        data = new VoxelGradient[dimX * dimY * dimZ];
        compute();
        maxmag = -1.0;
    }

    public VoxelGradient getGradient(int x, int y, int z) {
        //Utils.print("data size :"+data.length);  
        if (x < 0 || x >= volume.getDimX() || y < 0 || y >= volume.getDimY()
                || z < 0 || z >= volume.getDimZ()) {
            return zero;
        }
        return data[x + dimX * (y + dimY * z)];
    }
    
    public VoxelGradient getTriLinearGradient( float x, float y, float z ) {
        int xLow = (int) Math.floor(x);
        int yLow = (int) Math.floor(y);
        int zLow = (int) Math.floor(z);
        int xHigh = (int) Math.ceil(x);
        int yHigh = (int) Math.ceil(y);
        int zHigh = (int) Math.ceil(z);
        
        if (xLow < 0 || xLow >= dimX || yLow < 0 || yLow >= dimY
                || zLow < 0 || zLow >= dimZ ) {
            return zero;
        }
        if (xHigh < 0 || xHigh >= dimX || yHigh < 0 || yHigh >= dimY
                || zHigh < 0 || zHigh >= dimZ ) {
            return zero;
        }
        
        float a = xHigh == xLow ? 0 : x - xLow / (xHigh - xLow);
        float b = yHigh == yLow ? 0 : y - yLow / (yHigh - yLow);
        float g = zHigh == zLow ? 0 : z - zLow / (zHigh - zLow);
       
        float[] x0 = this.getGradient(xLow, yLow, zLow).xyzArray();
        float[] x1 = this.getGradient(xHigh, yLow, zLow).xyzArray();
        float[] x2 = this.getGradient(xLow, yLow, zHigh).xyzArray();
        float[] x3 = this.getGradient(xHigh, yLow, zHigh).xyzArray();
        float[] x4 = this.getGradient(xLow, yHigh, zLow).xyzArray();
        float[] x5 = this.getGradient(xHigh, yHigh, zLow).xyzArray();
        float[] x6 = this.getGradient(xLow, yHigh, zHigh).xyzArray();
        float[] x7 = this.getGradient(xHigh, yHigh, zHigh).xyzArray();
        
        float[] xyz = new float[3];
        for( int i = 0; i < 3; i ++ ){
            xyz[i] = (1 - a) * (1 - b) * (1 - g) * x0[i]
                    + a * (1 - b) * (1 - g) * x1[i] 
                    + (1 - a) * b * (1 - g) * x2[i]
                    + a * b * (1 - g) * x3[i]
                    + (1 - a) * (1 - b) * g * x4[i]
                    + a * (1 - b) * g * x5[i]
                    + (1 - a) * b * g * x6[i]
                    + a * b * g * x7[i];
        }
        
        return new VoxelGradient(xyz[0], xyz[1], xyz[2] );
    }

    
    public void setGradient(int x, int y, int z, VoxelGradient value) {
        data[x + dimX * (y + dimY * z)] = value;
    }

    public void setVoxel(int i, VoxelGradient value) {
        data[i] = value;
    }

    public VoxelGradient getVoxel(int i) {
        return data[i];
    }

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    public int getDimZ() {
        return dimZ;
    }

    private void compute() {

        // this just initializes all gradients to the vector (0,0,0)
        for (int i=0; i<data.length; i++) {
            data[i] = zero;
        }
      
        for (int i=1; i<dimX-1; i++) {
            for (int j=1; j<dimY-1; j++) { 
                for (int k=1; k<dimZ-1; k++) {
                    float x = (float)(.5*(volume.getVoxel(i+1,j,k)-volume.getVoxel(i-1,j,k)));
                    float y = (float)(.5*(volume.getVoxel(i,j+1,k)-volume.getVoxel(i,j-1,k)));
                    float z = (float)(.5*(volume.getVoxel(i,j,k+1)-volume.getVoxel(i,j,k-1)));
                    //float magnitude = (float) Math.sqrt(x*x + y*y + z*z);
                    data[i + dimX * (j + dimY * k)] = new VoxelGradient(x,y,z);
                }
            }  
        }                
    }
    
    public double getMaxGradientMagnitude() {
        if (maxmag >= 0) {
            return maxmag;
        } else {
            double magnitude = data[0].mag;
            for (int i=0; i<data.length; i++) {
                magnitude = data[i].mag > magnitude ? data[i].mag : magnitude;
            }   
            maxmag = magnitude;
            return magnitude;
        }
    }
    
    private int dimX, dimY, dimZ;
    private VoxelGradient zero = new VoxelGradient();
    VoxelGradient[] data;
    Volume volume;
    double maxmag;
}
