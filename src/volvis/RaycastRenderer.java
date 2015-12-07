/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import util.TFChangeListener;
import util.VectorMath;
import util.Utils;
import volume.GradientVolume;
import volume.VoxelGradient;
import volume.Volume;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;

    private boolean triLinearInterpolation = true;
    private boolean planeIntersection = true;
    private boolean lowerResolution = true;
    private boolean renderShading = false;

    public enum RENDER_MODE {

        SLICER, MIP, COMPOSITING, TWODTRANSFER
    }

    private RENDER_MODE mode = RENDER_MODE.SLICER;

    public void setMode(RENDER_MODE m) {
        this.mode = m;
        this.changed();
    }

    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());

        // uncomment this to initialize the TF with good starting values for the orange dataset 
        tFunc.setTestFunc();

        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());

        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }

    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }

    short getVoxel(double[] coord, boolean triLinear) {
        short res = 0;
        if (coord[0] < 0 || coord[0] >= volume.getDimX() || coord[1] < 0 || coord[1] >= volume.getDimY()
                || coord[2] < 0 || coord[2] >= volume.getDimZ()) {
            return res;
        }

        if (!triLinear) {
            int x = (int) Math.floor(coord[0]);
            int y = (int) Math.floor(coord[1]);
            int z = (int) Math.floor(coord[2]);
            res = volume.getVoxel(x, y, z);
        } else {
            res = (short) getTriLinearInterpolatedVoxel(coord);
        }
        /*short res;
         try {
         res = volume.getVoxel(x, y, z);
         } catch(Exception e) {
         res = 0;
         }*/
        return res;
    }

    void mip() {

        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
//        System.out.println(viewMatrix[0]);
        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        int[] kRange = new int[2];
        for (int j = 0; j < image.getHeight(); j += this.step()) {
            for (int i = 0; i < image.getWidth(); i += this.step()) {

                int maxIntensity = 0;

                kRange = optimalDepth(imageCenter, viewVec, uVec, vVec, i, j);
                for (int k = kRange[0]; k < kRange[1]; k++) {
                    // Get calculate new volumeCenter
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + viewVec[0] * (k) + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + viewVec[1] * (k) + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + viewVec[2] * (k) + volumeCenter[2];

                    int val = getVoxel(pixelCoord, triLinearInterpolation);
                    if (val > maxIntensity) {
                        maxIntensity = val;
                    }
                }

                // Map the intensity to a grey value by linear scaling
                voxelColor.r = maxIntensity / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = maxIntensity > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque

                long pixelColor = this.pixelColor(voxelColor);
                image.setRGB(i, j, (int) pixelColor);

                /* Low Resolution Rendering */
                if (this.step() > 1) {
                    this.interporateNeighbor(i, j, pixelColor);
                }
            }
        }
        Utils.print("MIP completed");
    }

    public long pixelColor(TFColor v) {
        int c_alpha = v.a <= 1.0 ? (int) Math.floor(v.a * 255) : 255;
        int c_red = v.r <= 1.0 ? (int) Math.floor(v.r * 255) : 255;
        int c_green = v.g <= 1.0 ? (int) Math.floor(v.g * 255) : 255;
        int c_blue = v.b <= 1.0 ? (int) Math.floor(v.b * 255) : 255;
        return this.binaryColor(new long[]{c_alpha, c_red, c_green, c_blue});
    }

    public void interporateNeighbor(int i, int j, long pixelColor) {
        long[] colorIJ = this.colorArray(pixelColor);
        for (int ni = -1; ni < this.step() && (ni + i) < image.getWidth() && (ni + i) >= 0; ni++) {
            for (int nj = -1; nj < this.step() && (nj + j) < image.getHeight() && (nj + j) >= 0; nj++) {
                if (ni == 0 && nj == 0) {
                    continue;
                }
                int dominator = 2;

                /* TODO: for corner pixels, dominator should be 1
                 Top Left : points (0,0), (0,1), (1,0)
                 Top Right, Left : Bottom Left, Bottom Right
                 */
                /* Get color from the pixel if possible ( from previous neighbor ) */
                long[] color = this.colorArray(image.getRGB(i + ni, j + nj));
                if ((ni + 1) % this.step() == 0 && (nj + 1) % this.step() == 0) {
                    dominator = 4;
                }
                for (int nc = 0; nc < 4; nc++) {
                    color[nc] += (colorIJ[nc] / dominator);
                    if (nc == 0) {
                        color[nc] = color[nc] > 0 ? 255 : 0;
                    }
                }
                image.setRGB(i + ni, j + nj, (int) this.binaryColor(color));

            }
        }
    }

    public long binaryColor(long[] rgba) {
        return (rgba[0] << 24) | (rgba[1] << 16) | (rgba[2] << 8) | rgba[3];
    }

    public long[] colorArray(long c) {
        return new long[]{((c & 0xff000000) >> 24), ((c & 0x00ff0000) >> 16), ((c & 0x0000ff00) >> 8), (c & 0x000000ff)};
    }

    void slicer() {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        for (int j = 0; j < image.getHeight(); j += this.step()) {
            for (int i = 0; i < image.getWidth(); i += this.step()) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];

                int val = getVoxel(pixelCoord, triLinearInterpolation);

                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                // voxelColor = tFunc.getColor(val);

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }

    }

    void compositing() {
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        short[][] sumIntensity = new short[image.getHeight()][image.getWidth()];
        short maxSumIntensity = 0;
        int[] kRange = new int[2];

        for (int j = 0; j < image.getHeight(); j += this.step()) {
            for (int i = 0; i < image.getWidth(); i += this.step()) {
                sumIntensity[i][j] = 0;
                kRange = optimalDepth(imageCenter, viewVec, uVec, vVec, i, j);

                TFColor compositingColor = new TFColor(0, 0, 0, 0);
                for (int k = kRange[1] - 1; k >= kRange[0]; k--) {
                    // Get calculate new volumeCenter
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + viewVec[0] * (k) + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + viewVec[1] * (k) + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + viewVec[2] * (k) + volumeCenter[2];

                    int val = getVoxel(pixelCoord, triLinearInterpolation);

                    voxelColor = tFunc.getColor(val);

                    compositingColor.r = voxelColor.r * voxelColor.a + (1 - voxelColor.a) * compositingColor.r;
                    compositingColor.g = voxelColor.g * voxelColor.a + (1 - voxelColor.a) * compositingColor.g;
                    compositingColor.b = voxelColor.b * voxelColor.a + (1 - voxelColor.a) * compositingColor.b;

                    compositingColor.a = (1 - voxelColor.a) * compositingColor.a;

                }
                compositingColor.a = 1 - compositingColor.a;
                long pixelColor = this.pixelColor(compositingColor);

                image.setRGB(i, j, (int) pixelColor);

                if (this.step() > 1) {
                    this.interporateNeighbor(i, j, pixelColor);
                }

            }
        }

    }

    void twoDTransfer() {

        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        
        double[] reverseView = new double[3];
        VectorMath.setVector( reverseView, -viewMatrix[2], -viewMatrix[6], -viewMatrix[10] );
                
        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        short[][] sumIntensity = new short[image.getHeight()][image.getWidth()];
        short maxSumIntensity = 0;
        int[] kRange = new int[2];
        double[] rgb;

        for (int j = 0; j < image.getHeight(); j += this.step()) {
            for (int i = 0; i < image.getWidth(); i += this.step()) {
                sumIntensity[i][j] = 0;
                kRange = optimalDepth(imageCenter, viewVec, uVec, vVec, i, j);

                TFColor compositingColor = new TFColor(0, 0, 0, 0);
                VoxelGradient gradient;
                VoxelGradient maxGradient;
                for (int k = kRange[1]; k > kRange[0]; k--) {
                    //System.out.println(kRange[0]+"::"+kRange[1]);

                    // Get calculate new volumeCenter
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + viewVec[0] * (k) + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + viewVec[1] * (k) + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + viewVec[2] * (k) + volumeCenter[2];

                    int val = getVoxel(pixelCoord, triLinearInterpolation);

                    
                    voxelColor = tfEditor2D.triangleWidget.color.clone();

                    gradient = gradients.getTriLinearGradient((float) pixelCoord[0], (float) pixelCoord[1], (float) pixelCoord[2]);
                    double dotProduct = VectorMath.dotproduct(reverseView, gradient.normalisedVector());
                    double opacity = computeOpacity(val, gradient);
                    
                    if (this.renderShading) {    
                        rgb = new double[]{0, 0, 0};
                        if (gradient.mag > 0) {
                            double[] compRGB = new double[]{voxelColor.r, voxelColor.g, voxelColor.b};
                            for (int z = 0; z < 3; z++) {
                                rgb[z] = 0.1 + compRGB[z] * 0.7 * dotProduct + 0.2 * Math.pow(dotProduct, 10);
                            }
                            Utils.setTFColorFromArray(voxelColor, rgb);
                        }
                    }
                   
                    compositingColor.r = voxelColor.r * opacity + (1 - opacity) * compositingColor.r;
                    compositingColor.g = voxelColor.g * opacity + (1 - opacity) * compositingColor.g;
                    compositingColor.b = voxelColor.b * opacity + (1 - opacity) * compositingColor.b;

                
                    //compositingColor.a = (1 - voxelColor.a) * compositingColor.a;
                    compositingColor.a = (1 - opacity) * compositingColor.a;


                }

                compositingColor.a = 1 - compositingColor.a;

                long pixelColor = this.pixelColor(compositingColor);

                image.setRGB(i, j, (int) pixelColor);                

                if (this.step() > 1) {
                    this.interporateNeighbor(i, j, pixelColor);
                }

            }
        }

    }

    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }

    @Override
    public void visualize(GL2 gl) {

        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        this.render();

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();
        try {
            File outputfile = new File("MyFile.png");
            ImageIO.write(image, "png", outputfile);
        } catch (IOException ex) {
            Logger.getLogger(RaycastRenderer.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

    }
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];

    @Override
    public void changed() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }

    public void render() {
        Utils.print("Render Mode : " + this.mode + " with step " + this.step());
        long startTime = System.currentTimeMillis();

        switch (this.mode) {
            case MIP:
                this.mip();
                break;
            case SLICER:
                this.slicer();
                break;
            case COMPOSITING:
                this.compositing();
                break;
            case TWODTRANSFER:
                this.twoDTransfer();
                break;

        }

        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

    }

    private int[] optimalDepth(int imageCenter, double[] viewVec, double[] uVec, double[] vVec, int i, int j) {
        int kStart = Integer.MIN_VALUE;
        int kEnd = Integer.MAX_VALUE;
        double[] pixelCoord = new double[3];

        if (!this.planeIntersection) {
            return new int[]{0, volume.getDiagonalDepth()};
        }

        //set vloume dimensions volume[x,y,z][low,high] = vloume[0,1,2][0,1]
        int[][] volumeBoundary = new int[3][2];
        volumeBoundary[0][0] = -volume.getDimX() / 2;
        volumeBoundary[0][1] = volume.getDimX() / 2;
        volumeBoundary[1][0] = -volume.getDimY() / 2;
        volumeBoundary[1][1] = volume.getDimY() / 2;
        volumeBoundary[2][0] = -volume.getDimZ() / 2;
        volumeBoundary[2][1] = volume.getDimZ() / 2;

        //origin[x,y,z]
        double[] origin = new double[3];

        int[] kList = new int[2];

        for (int l = 0; l < 3; l++) {

            //set origin  origin[x,y,z] = origin[0,1,2]
            origin[l] = (i - imageCenter) * uVec[l] + (j - imageCenter) * vVec[l];

            //if origin not between the slabs then return
            if (viewVec[l] == 0) {
                if ((origin[l] < volumeBoundary[l][0] || origin[l] > volumeBoundary[l][1])) {
                    return new int[]{0, 0};
                }
            } else {

                //for each dimension find kLow/kHigh . k[x,y,z][low,high] : k[0,1,2][0,1]
                kList[0] = (int) ((volumeBoundary[l][0] - origin[l]) / viewVec[l]);
                kList[1] = (int) ((volumeBoundary[l][1] - origin[l]) / viewVec[l]);

                // if kLow > kHigh, swap
                if (kList[0] > kList[1]) {
                    int tmp = kList[0];
                    kList[0] = kList[1];
                    kList[1] = tmp;
                }
                //if kLow > kStart , kStart = kLow
                if (kList[0] > kStart) {
                    kStart = kList[0];
                }
                //if kHigh < kEnd , kEnd = kHigh
                if (kList[1] < kEnd) {
                    kEnd = kList[1];
                }
                //
                if (kStart > kEnd || kEnd < 0) {
                    return new int[]{0, 0};
                }
            }
        }
        return new int[]{kStart, kEnd};

    }

    private int step() {
        if (this.interactiveMode && this.lowerResolution) {
            return 2;
        }
        return 1;
    }

    public void toggleTriLinear() {
        this.triLinearInterpolation = !this.triLinearInterpolation;
        Utils.print("Toggle Tri-linear Interpolation : " + this.triLinearInterpolation);
        this.changed();
    }

    public void togglePlaneIntersectionMode() {
        this.planeIntersection = !this.planeIntersection;
        this.render();
        Utils.print("Toggle Plane Intersection : " + this.planeIntersection);
    }

    public void toggleLowerResolution() {
        this.lowerResolution = !this.lowerResolution;
        this.render();
        Utils.print("Toggle Lower Resolution : " + this.lowerResolution);

    }
    
    public void toggleShading(){
        this.renderShading = !this.renderShading;
        this.changed();
        Utils.print("Toggle Shading : " + this.renderShading );
    }

    private double getTriLinearInterpolatedVoxel(double[] coord) {
        int xLow = (int) Math.floor(coord[0]);
        int yLow = (int) Math.floor(coord[1]);
        int zLow = (int) Math.floor(coord[2]);
        int xHigh = (int) Math.ceil(coord[0]);
        int yHigh = (int) Math.ceil(coord[1]);
        int zHigh = (int) Math.ceil(coord[2]);

        if (xLow < 0 || xLow >= volume.getDimX() || yLow < 0 || yLow >= volume.getDimY()
                || zLow < 0 || zLow >= volume.getDimZ()) {
            return 0;
        }
        if (xHigh < 0 || xHigh >= volume.getDimX() || yHigh < 0 || yHigh >= volume.getDimY()
                || zHigh < 0 || zHigh >= volume.getDimZ()) {
            return 0;
        }
        double a = xHigh == xLow ? 0 : coord[0] - xLow / (xHigh - xLow);
        double b = yHigh == yLow ? 0 : coord[1] - yLow / (yHigh - yLow);
        double g = zHigh == zLow ? 0 : coord[2] - zLow / (zHigh - zLow);

        short x0 = volume.getVoxel(xLow, yLow, zLow);
        short x1 = volume.getVoxel(xHigh, yLow, zLow);
        short x2 = volume.getVoxel(xLow, yLow, zHigh);
        short x3 = volume.getVoxel(xHigh, yLow, zHigh);
        short x4 = volume.getVoxel(xLow, yHigh, zLow);
        short x5 = volume.getVoxel(xHigh, yHigh, zLow);
        short x6 = volume.getVoxel(xLow, yHigh, zHigh);
        short x7 = volume.getVoxel(xHigh, yHigh, zHigh);
        return (1 - a) * (1 - b) * (1 - g) * x0 + a * (1 - b) * (1 - g) * x1 + (1 - a) * b * (1 - g) * x2 + a * b * (1 - g) * x3 + (1 - a) * (1 - b) * g * x4 + a * (1 - b) * g * x5 + (1 - a) * b * g * x6 + a * b * g * x7;
    }

//    private double computeOpacity(int x, int y, int z, double intensity) {
//        //int alpha = tfEditor2D.getColorModel().getAlpha(winWidth);
//        TFColor color = tfEditor2D.triangleWidget.color;
//        short baseIntensity = tfEditor2D.triangleWidget.baseIntensity;
//        double radius = tfEditor2D.triangleWidget.radius;
//        VoxelGradient voxelGradient = null;
//        try {
//            voxelGradient = gradients.getTriLinearGradient(x, y, z);
//        } catch (Exception e) {
//            Utils.printVector(new int[]{x, y, z});
//            Utils.print("x + dimX * (y + dimY * z) :" + (x + volume.getDimX() * (y + volume.getDimY() * z)));
//            e.printStackTrace();
//            System.exit(1);
//        }
//        float gradientMagnitude = voxelGradient.mag;
//
//        if (gradientMagnitude == 0 && intensity == baseIntensity) {
//            return color.a;
//        } else if (gradientMagnitude > 0 && (intensity - radius * gradientMagnitude <= baseIntensity) && (baseIntensity <= intensity + radius * gradientMagnitude)) {
//            return color.a * (1 - (1 / radius) * Math.abs((baseIntensity - intensity) / (gradientMagnitude)));
//        }
//        //
//        return 0.0;
//    }

    private double computeOpacity( double intensity, VoxelGradient voxelGradient) {
        TFColor color = tfEditor2D.triangleWidget.color;
        short baseIntensity = tfEditor2D.triangleWidget.baseIntensity;
        double radius = tfEditor2D.triangleWidget.radius;
      
        float gradientMagnitude = voxelGradient.mag;

        if (gradientMagnitude == 0 && intensity == baseIntensity) {
            return color.a;
        } else if (gradientMagnitude > 0 && (intensity - radius * gradientMagnitude <= baseIntensity) && (baseIntensity <= intensity + radius * gradientMagnitude)) {
            return color.a * (1 - (1 / radius) * Math.abs((baseIntensity - intensity) / (gradientMagnitude)));
        }
        //
        return 0.0;
    }

}
