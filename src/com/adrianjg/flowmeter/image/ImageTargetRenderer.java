/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

/*
 * Main class that deals with the rendering of the object.
 */

package com.adrianjg.flowmeter.image;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.qualcomm.vuforia.CameraCalibration;
import com.qualcomm.vuforia.CameraDevice;
import com.qualcomm.vuforia.Frame;
import com.qualcomm.vuforia.Image;
import com.qualcomm.vuforia.Matrix34F;
import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Rectangle;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.TrackableResult;
import com.qualcomm.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.qualcomm.vuforia.Vec3F;
import com.qualcomm.vuforia.VideoBackgroundConfig;
import com.qualcomm.vuforia.VideoMode;
import com.qualcomm.vuforia.Vuforia;

import com.adrianjg.flowmeter.ApplicationSession;
import com.adrianjg.flowmeter.utils.Arrow;
import com.adrianjg.flowmeter.utils.CubeShaders;
import com.adrianjg.flowmeter.utils.LineShaders;
import com.adrianjg.flowmeter.utils.LoadingDialogHandler;
import com.adrianjg.flowmeter.utils.SampleUtils;
import com.adrianjg.flowmeter.utils.Texture;

// The renderer class for the ImageTargets sample. 
public class ImageTargetRenderer implements GLSurfaceView.Renderer {

	// General variables
	private static final String LOGTAG = "ImageTargetRenderer";
	private ApplicationSession vuforiaAppSession;
	private ImageTargets mActivity;
	private Vector<Texture> mTextures;
	private int numVertices = 180;

	private int shaderProgramID;
	private int vertexHandle;
	private int normalHandle;
	private int textureCoordHandle;
	private int mvpMatrixHandle;
	private int texSampler2DHandle;

	// Model variables
	private Arrow mArrow;
	private Renderer mRenderer;
	boolean mIsActive = false;

	// Signal processing variables
	Handler activityHandler = new Handler(Looper.getMainLooper());
	CameraCalibration cameraCalib;
	private final int RGB565_FORMAT = 1;
	private Vec3F[][] pixelLevelLocations;
	private double[] colorSpectrum;
	private double colorCount;
	private int[][] colorPixels;
	private Bitmap cameraBitmap;
	private Vec3F measureLoc;
	
	// Open GL magic
	private int vbShaderProgramID = 0;
	private int vbVertexHandle = 0;
	private int colorVertexHandle = 0;
	private int lineOpacityHandle = 0;
	private int lineColorHandle = 0;
	private int mvpMatrixButtonsHandle = 0;
	private Rectangle[] renderRectangle;
	private LinkedList<Double> last10Obs;
	
	
	float[] tempvec = new float[]{0f, 0f, 0f};
	float chi = 0f;
	float phi = 0f;
	float maxZ = 0f;
	
	private float RedRight = 11f;
    private float GreenLeft = 21f;
    private boolean found = false;
    
	private Rectangle[] redRectangle;
	private Rectangle[] yellowRectangle;
	private Rectangle[] greenRectangle;
	
	public ImageTargetRenderer(ImageTargets activity, ApplicationSession session) {
		mActivity = activity;
		vuforiaAppSession = session;
		

		int[] margins = mActivity.calculateMargins();
		RedRight = Math.round(scale_num(margins[1], 0, 900, -3.625f, 35));
		GreenLeft = Math.round(scale_num(margins[0], 0, 900, -3.625f, 35));
	}

	/**
	 * Initialize rendering - set up and initialize variables that will be displayed.
	 */
	private void initRendering() {
				
		mArrow = new Arrow();
		mRenderer = Renderer.getInstance();
		
		cameraCalib = CameraDevice.getInstance().getCameraCalibration();
		float[] resolution = cameraCalib.getSize().getData();
		cameraBitmap = Bitmap.createBitmap((int) resolution[0],
				(int) resolution[1], Bitmap.Config.RGB_565);
		
		colorSpectrum = new double[] { 4, 6, 8, 10, 12, 14 };
		last10Obs = new LinkedList<Double>();

			    // multiple sampling inside	    
		
		renderRectangle = new Rectangle[2];
		renderRectangle[0] = new Rectangle(-3.625f, -7.0f, 35f, -7.0f);
		renderRectangle[1] = new Rectangle(-3.625f, 3.625f, 3.625f, -3.625f);
		System.out.println(RedRight + "   " + GreenLeft);
		redRectangle = new Rectangle[1];
		redRectangle[0] = new Rectangle(-3.625f, -8.0f, RedRight, -8.0f);
		
		yellowRectangle = new Rectangle[1];
		yellowRectangle[0] = new Rectangle(RedRight, -8.0f, GreenLeft, -8.0f);
		
		greenRectangle = new Rectangle[1];
		greenRectangle[0] = new Rectangle(GreenLeft, -8.0f, 35f, -8.0f);
		

		// Initialize Arrow rendering
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
				: 1.0f);
		for (Texture t : mTextures) {
			GLES20.glGenTextures(1, t.mTextureID, 0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
					t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
					GLES20.GL_UNSIGNED_BYTE, t.mData);
		}
		shaderProgramID = SampleUtils.createProgramFromShaderSrc(
				CubeShaders.CUBE_MESH_VERTEX_SHADER,
				CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

		vertexHandle = GLES20.glGetAttribLocation(shaderProgramID, "vertexPosition");
		normalHandle = GLES20.glGetAttribLocation(shaderProgramID, "vertexNormal");
		textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID, "vertexTexCoord");
		mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID, "modelViewProjectionMatrix");
		texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID, "texSampler2D");
		
		vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(
				LineShaders.LINE_VERTEX_SHADER,
				LineShaders.LINE_FRAGMENT_SHADER);
		vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexPosition");
		mvpMatrixButtonsHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "modelViewProjectionMatrix");
		lineOpacityHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "opacity");
		lineColorHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "color");

		mActivity.loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
	}

	// The render function.
	private void renderFrame() {
		
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		
		State state = mRenderer.begin();
		mRenderer.drawVideoBackground();
		
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		GLES20.glEnable(GLES20.GL_CULL_FACE);
		GLES20.glCullFace(GLES20.GL_BACK);
		if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
			GLES20.glFrontFace(GLES20.GL_CW); // Front camera
		else
			GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

		for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
			
			TrackableResult result = state.getTrackableResult(tIdx);
			Matrix44F modelViewMatrix_Vuforia = Tool.convertPose2GLMatrix(result.getPose());

			float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();
			float[] modelViewMatrixSignal = modelViewMatrix_Vuforia.getData();
			
			float[] modelViewMatrixG = modelViewMatrix_Vuforia.getData();
			float[] modelViewMatrixY = modelViewMatrix_Vuforia.getData();
			float[] modelViewMatrixR = modelViewMatrix_Vuforia.getData();
			
			float[] vbVertices = initGLVertices();
			float[] greenVertices = greenGLVertices();
			float[] yellowVertices = yellowGLVertices();
			float[] redVertices = redGLVertices();
			
			int textureIndex = 0;
			
			float[] modelViewProjection = new float[16];
			float[] modelViewProjectionSignal = new float[16];
			
			float[] modelViewProjectionG = new float[16];
			float[] modelViewProjectionY = new float[16];
			float[] modelViewProjectionR = new float[16];
			
			/**
			 * AR rendering
			 */
			Matrix.translateM(modelViewMatrix, 0, tempvec[0] + chi, -115f, 30.0f);
			Matrix.scaleM(modelViewMatrix, 0, 65.0f, 65.0f, 65.0f);
			Matrix.rotateM(modelViewMatrix, 0, 180.0f, 0.0f, 0.0f, 1.0f);
			
			Matrix.multiplyMM(modelViewProjection, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrix, 0);
			Matrix.multiplyMM(modelViewProjectionG, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrix, 0);
			Matrix.multiplyMM(modelViewProjectionY, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrix, 0);
			Matrix.multiplyMM(modelViewProjectionR, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrix, 0);

			GLES20.glUseProgram(shaderProgramID);
			GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 0, mArrow.getVertices());
			GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, mArrow.getNormals());
			GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mArrow.getTexCoords());
			GLES20.glEnableVertexAttribArray(vertexHandle);
			GLES20.glEnableVertexAttribArray(normalHandle);
			GLES20.glEnableVertexAttribArray(textureCoordHandle);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(textureIndex).mTextureID[0]);
			GLES20.glUniform1i(texSampler2DHandle, 0);
			GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjection, 0);

			if(found == true) {
				//Render the arrow
				GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, numVertices);
				GLES20.glDisableVertexAttribArray(vertexHandle);
				GLES20.glDisableVertexAttribArray(normalHandle);
				GLES20.glDisableVertexAttribArray(textureCoordHandle);
				SampleUtils.checkGLError("Render Frame");
			}
				
			//Structure rendering
			Matrix.scaleM(modelViewMatrixSignal, 0, 35, 35, 35);
			Matrix.multiplyMM(modelViewProjectionSignal, 0, vuforiaAppSession
					.getProjectionMatrix().getData(), 0, modelViewMatrixSignal,
					0);
			GLES20.glUseProgram(vbShaderProgramID);
			GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT,
					false, 0, fillBuffer(vbVertices));
			GLES20.glEnableVertexAttribArray(vbVertexHandle);
			GLES20.glUniform1f(lineOpacityHandle, 1.0f);
			GLES20.glUniform3f(lineColorHandle, 0.0f, 1.0f, 0.0f);
			GLES20.glUniformMatrix4fv(mvpMatrixButtonsHandle, 1, false,
					modelViewProjectionSignal, 0);
			GLES20.glDrawArrays(GLES20.GL_LINES, 0, 8 * renderRectangle.length);
						
			// draws the horizontal color bar (GYR)
			Matrix.scaleM(modelViewMatrixG, 0, 35, 35, 35);
			Matrix.multiplyMM(modelViewProjectionG, 0, vuforiaAppSession
					.getProjectionMatrix().getData(), 0, modelViewMatrixG,
					0);
			GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT,
					false, 0, fillBuffer(greenVertices));
			GLES20.glUniform3f(lineColorHandle, 0.0f, 1.0f, 0.0f);
			GLES20.glUniformMatrix4fv(mvpMatrixButtonsHandle, 1, false,
					modelViewProjectionG, 0);
			GLES20.glLineWidth(15f);
			GLES20.glDrawArrays(GLES20.GL_LINES, 0, 8 * greenRectangle.length);
			
			Matrix.scaleM(modelViewMatrixY, 0, 35, 35, 35);
			Matrix.multiplyMM(modelViewProjectionY, 0, vuforiaAppSession
					.getProjectionMatrix().getData(), 0, modelViewMatrixY,
					0);
			GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT,
					false, 0, fillBuffer(yellowVertices));
			GLES20.glUniform3f(lineColorHandle, 1.0f, 1.0f, 0.0f);
			GLES20.glUniformMatrix4fv(mvpMatrixButtonsHandle, 1, false,
					modelViewProjectionY, 0);
			GLES20.glDrawArrays(GLES20.GL_LINES, 0, 8 * yellowRectangle.length);
			
			Matrix.scaleM(modelViewMatrixR, 0, 35, 35, 35);
			Matrix.multiplyMM(modelViewProjectionR, 0, vuforiaAppSession
					.getProjectionMatrix().getData(), 0, modelViewMatrixR,
					0);
			GLES20.glVertexAttribPointer(colorVertexHandle, 3, GLES20.GL_FLOAT,
					false, 0, fillBuffer(redVertices));
			GLES20.glUniform3f(lineColorHandle, 1.0f, 0.0f, 0.0f);
			GLES20.glUniformMatrix4fv(mvpMatrixButtonsHandle, 1, false,
					modelViewProjectionR, 0);
			GLES20.glDrawArrays(GLES20.GL_LINES, 0, 8 * redRectangle.length);
			
			GLES20.glDisableVertexAttribArray(vbVertexHandle);
			GLES20.glLineWidth(1);

			
			cameraBitmap = getCameraBitmap(state);
			Matrix34F pose = state.getTrackableResult(0).getPose();
            
			float[] positionVector = pose.getData();	
			tempvec = positionVector;
			
			pixelLevelLocations = new Vec3F[6][];
			float x = modelViewMatrix[0] + 100;
			float y2 = 0f;
			
			pixelLevelLocations[0] = getColorLevelArea(x, y2+3, 0);
			pixelLevelLocations[1] = getColorLevelArea(x, y2+2, 0);
			pixelLevelLocations[2] = getColorLevelArea(x, y2+1, 0);
			pixelLevelLocations[3] = getColorLevelArea(x, y2, 0);
			pixelLevelLocations[4] = getColorLevelArea(x, y2-1, 0);
			pixelLevelLocations[5] = getColorLevelArea(x, y2-2, 0);

			final float xMeasure = modelViewMatrixSignal[0];
			final float yMeasure = modelViewMatrixSignal[3]-275;
			int measuredPixel = 0;
			
			phi = scale_num(chi, 0, 1200, 100, 800);
			
			float bx = chi;
			float by = -200f;
			float bz = 0;
			
			measureLoc = new Vec3F((int)bx, (int)by, (int)bz);
	
			measuredPixel = getPixelsOnBitmap(new Vec3F[] {measureLoc}, pose)[0];
			int[][] pixels = new int[colorSpectrum.length][];
			for (int i = 0; i < colorSpectrum.length; i++) {
				int[] ps = getPixelsOnBitmap(pixelLevelLocations[i], pose);
				pixels[i] = averagePixels(ps);
			}
			
			int[] reds = new int[colorSpectrum.length];
			for (int i = 0; i < colorSpectrum.length; i++) {
				reds[i] = pixels[i][0];
			}

			double measured = byteSamplingModel(reds, Color.red(measuredPixel));
			colorPixels = pixels;
			final int measurementOnDisplay = measuredPixel;

			last10Obs.add(measured);
			if (last10Obs.size() > 10) {
				last10Obs.removeFirst();
			}

			double sum = 0;
			for (double m : last10Obs) {
				sum += m;
			}
			colorCount = sum / 10;
			colorCount = 0;
			final int message;
			
			int observation = mActivity.getCurrentColor();
			int measPosition = 0;
			
			int r = (observation)&0xFF;
			int g = (observation>>8)&0xFF;
			int b = (observation>>16)&0xFF;
			
			if(r<=45 && g<= 50 && b <= 150 && b >= 120) {
				found = true;
				measPosition = calculateReading(
						scale_num((int)bx, 0, 1200, 0, 10)
				);
				//measPosition = (int)bx;
				message = measPosition;
			} else {
				found = false;
				measPosition = 0;
				message = (int)bx;//3 - x; 7 - ; 11 - z
			
				if(chi > 1200) {
					chi = -1;
				}
				chi = chi + 3;
			}
			
			activityHandler.post(new Runnable() {
				public void run() {
					mActivity.updateByteCount(message, measurementOnDisplay, xMeasure, yMeasure, found);
				}
			});
			
			SampleUtils.checkGLError("FrameMarkers render frame");
		}
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
		mRenderer.end();
	}		
	
	/**
	 * Sampling utils
	 */
	private Vec3F[] getColorLevelArea(float cx, float cy, float cz) {
		Vec3F[] samples = new Vec3F[9];
		int r = 3, c = 3;
		for (int i = 0; i < r; i++) {
			for (int j = 0; j < c; j++) {
				samples[i * r + j] = new Vec3F(cx + (i - 1) * 0.1f, cy
						+ (j - 1) * 0.1f, cz);
			}
		}
		return samples;
	}
	
	public double getColorCount() {
		return colorCount;
	}
	
	private float scale_num(float val, float min_old, float max_old, float min_new,
			float max_new) {
		if (val < min_old) {
			val = min_old;
		}
		if (val > max_old) {
			val = max_old;
		}
		float valor = ((max_new - min_new) * (val - min_old) / (max_old - min_old))
				+ min_new;
		return valor;
	}

	public String[] getColorPixels() {
		String[] result = new String[colorSpectrum.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = String.format("%d#%d#%d#%d", (int) colorSpectrum[i],
					colorPixels[i][0], colorPixels[i][1], colorPixels[i][2]);
		}
		return result;
	}

	private int[] averagePixels(int[] pixels) {
		int redSum = 0;
		int blueSum = 0;
		int greenSum = 0;
		for (int i = 0; i < pixels.length; i++) {
			redSum += Color.red(pixels[i]);
			blueSum += Color.blue(pixels[i]);
			greenSum += Color.green(pixels[i]);
		}
		return new int[] { redSum / pixels.length, greenSum / pixels.length,
				blueSum / pixels.length };
	}

	private double byteSamplingModel(int[] pixels, int measurement) {
		SimpleRegression model = new SimpleRegression(true);
		double[][] data = new double[colorSpectrum.length][2];
		for (int i = 0; i < colorSpectrum.length; i++) {
			data[i][0] = (double) pixels[i];
			data[i][1] = (double) colorSpectrum[i];
		}
		model.addData(data);
		return model.predict(measurement);
	}

	private int[] getPixelsOnBitmap(Vec3F[] vectors, Matrix34F pose) {
		int[] pixels = new int[vectors.length];
		for (int i = 0; i < vectors.length; i++) {
			float[] point = Tool.projectPoint(cameraCalib, pose, vectors[i]).getData();
			
			int x = Math.round(point[0]);
			int y = Math.round(point[1]);
			
			if(y>= cameraBitmap.getHeight()) {
				y = cameraBitmap.getHeight() - 1;
			}
			
			if(x>= cameraBitmap.getWidth()) {
				x = cameraBitmap.getWidth() - 1;
			}
			
			pixels[i] = cameraBitmap.getPixel(x, y);
		}
		
		return pixels;
	}
	
	private Bitmap getCameraBitmap(State state) {

		Image image = null;
		Frame frame = state.getFrame();
		for (int i = 0; i < frame.getNumImages(); i++) {
			image = frame.getImage(i);
			if (image.getFormat() == RGB565_FORMAT) {
				break;
			}
		}
		if (image != null) {
			ByteBuffer buffer = image.getPixels();
			cameraBitmap.copyPixelsFromBuffer(buffer);
			return cameraBitmap;
		} else {
			Log.e(LOGTAG, "image not found.");
		}
		return null;
	}

	public void setTextures(Vector<Texture> textures) {
		mTextures = textures;
	}
	
	private int calculateReading(float x) {
		return (int)(3.055842532*x*x*x + 8.161672661*x*x + 121.6430461*x + 29.04681895);
	}
	
	/**
	 * Render utils
	 */
	private float[] initGLVertices() {
		float[] vertices = new float[renderRectangle.length * 24];
		int vInd = 0;

		for (Rectangle rect : renderRectangle) {
			vertices[vInd] = rect.getLeftTopX();
			vertices[vInd + 1] = rect.getLeftTopY();
			vertices[vInd + 2] = 0.0f;
			vertices[vInd + 3] = rect.getRightBottomX();
			vertices[vInd + 4] = rect.getLeftTopY();
			vertices[vInd + 5] = 0.0f;
			vertices[vInd + 6] = rect.getRightBottomX();
			vertices[vInd + 7] = rect.getLeftTopY();
			vertices[vInd + 8] = 0.0f;
			vertices[vInd + 9] = rect.getRightBottomX();
			vertices[vInd + 10] = rect.getRightBottomY();
			vertices[vInd + 11] = 0.0f;
			vertices[vInd + 12] = rect.getRightBottomX();
			vertices[vInd + 13] = rect.getRightBottomY();
			vertices[vInd + 14] = 0.0f;
			vertices[vInd + 15] = rect.getLeftTopX();
			vertices[vInd + 16] = rect.getRightBottomY();
			vertices[vInd + 17] = 0.0f;
			vertices[vInd + 18] = rect.getLeftTopX();
			vertices[vInd + 19] = rect.getRightBottomY();
			vertices[vInd + 20] = 0.0f;
			vertices[vInd + 21] = rect.getLeftTopX();
			vertices[vInd + 22] = rect.getLeftTopY();
			vertices[vInd + 23] = 0.0f;
			vInd += 24;
		}
		return vertices;
	}
	
	private float[] redGLVertices() {
		float[] vertices = new float[redRectangle.length * 24];
		int vInd = 0;

		for (Rectangle rect : redRectangle) {
			vertices[vInd] = rect.getLeftTopX();
			vertices[vInd + 1] = rect.getLeftTopY();
			vertices[vInd + 2] = 0.0f;
			vertices[vInd + 3] = rect.getRightBottomX();
			vertices[vInd + 4] = rect.getLeftTopY();
			vertices[vInd + 5] = 0.0f;
			vertices[vInd + 6] = rect.getRightBottomX();
			vertices[vInd + 7] = rect.getLeftTopY();
			vertices[vInd + 8] = 0.0f;
			vertices[vInd + 9] = rect.getRightBottomX();
			vertices[vInd + 10] = rect.getRightBottomY();
			vertices[vInd + 11] = 0.0f;
			vertices[vInd + 12] = rect.getRightBottomX();
			vertices[vInd + 13] = rect.getRightBottomY();
			vertices[vInd + 14] = 0.0f;
			vertices[vInd + 15] = rect.getLeftTopX();
			vertices[vInd + 16] = rect.getRightBottomY();
			vertices[vInd + 17] = 0.0f;
			vertices[vInd + 18] = rect.getLeftTopX();
			vertices[vInd + 19] = rect.getRightBottomY();
			vertices[vInd + 20] = 0.0f;
			vertices[vInd + 21] = rect.getLeftTopX();
			vertices[vInd + 22] = rect.getLeftTopY();
			vertices[vInd + 23] = 0.0f;
			vInd += 24;
		}
		return vertices;
	}
	
	private float[] yellowGLVertices() {
		float[] vertices = new float[yellowRectangle.length * 24];
		int vInd = 0;

		for (Rectangle rect : yellowRectangle) {
			vertices[vInd] = rect.getLeftTopX();
			vertices[vInd + 1] = rect.getLeftTopY();
			vertices[vInd + 2] = 0.0f;
			vertices[vInd + 3] = rect.getRightBottomX();
			vertices[vInd + 4] = rect.getLeftTopY();
			vertices[vInd + 5] = 0.0f;
			vertices[vInd + 6] = rect.getRightBottomX();
			vertices[vInd + 7] = rect.getLeftTopY();
			vertices[vInd + 8] = 0.0f;
			vertices[vInd + 9] = rect.getRightBottomX();
			vertices[vInd + 10] = rect.getRightBottomY();
			vertices[vInd + 11] = 0.0f;
			vertices[vInd + 12] = rect.getRightBottomX();
			vertices[vInd + 13] = rect.getRightBottomY();
			vertices[vInd + 14] = 0.0f;
			vertices[vInd + 15] = rect.getLeftTopX();
			vertices[vInd + 16] = rect.getRightBottomY();
			vertices[vInd + 17] = 0.0f;
			vertices[vInd + 18] = rect.getLeftTopX();
			vertices[vInd + 19] = rect.getRightBottomY();
			vertices[vInd + 20] = 0.0f;
			vertices[vInd + 21] = rect.getLeftTopX();
			vertices[vInd + 22] = rect.getLeftTopY();
			vertices[vInd + 23] = 0.0f;
			vInd += 24;
		}
		return vertices;
	}
	
	private float[] greenGLVertices() {
		float[] vertices = new float[greenRectangle.length * 24];
		int vInd = 0;

		for (Rectangle rect : greenRectangle) {
			vertices[vInd] = rect.getLeftTopX();
			vertices[vInd + 1] = rect.getLeftTopY();
			vertices[vInd + 2] = 0.0f;
			vertices[vInd + 3] = rect.getRightBottomX();
			vertices[vInd + 4] = rect.getLeftTopY();
			vertices[vInd + 5] = 0.0f;
			vertices[vInd + 6] = rect.getRightBottomX();
			vertices[vInd + 7] = rect.getLeftTopY();
			vertices[vInd + 8] = 0.0f;
			vertices[vInd + 9] = rect.getRightBottomX();
			vertices[vInd + 10] = rect.getRightBottomY();
			vertices[vInd + 11] = 0.0f;
			vertices[vInd + 12] = rect.getRightBottomX();
			vertices[vInd + 13] = rect.getRightBottomY();
			vertices[vInd + 14] = 0.0f;
			vertices[vInd + 15] = rect.getLeftTopX();
			vertices[vInd + 16] = rect.getRightBottomY();
			vertices[vInd + 17] = 0.0f;
			vertices[vInd + 18] = rect.getLeftTopX();
			vertices[vInd + 19] = rect.getRightBottomY();
			vertices[vInd + 20] = 0.0f;
			vertices[vInd + 21] = rect.getLeftTopX();
			vertices[vInd + 22] = rect.getLeftTopY();
			vertices[vInd + 23] = 0.0f;
			vInd += 24;
		}
		return vertices;
	}

	private Buffer fillBuffer(float[] array) {
		ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		for (float d : array)
			bb.putFloat(d);
		bb.rewind();
		return bb;
	}
	
	@Override
	public void onDrawFrame(GL10 gl) {
		if (!mIsActive)
			return;
		renderFrame();
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");
		initRendering();
		vuforiaAppSession.onSurfaceCreated();
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");
		vuforiaAppSession.onSurfaceChanged(width, height);
	}
	
	public int[] getCameraDetails() {
		VideoBackgroundConfig config = mRenderer.getInstance().getVideoBackgroundConfig();
		int[] details = new int[2];
		
		details[0] = config.getSize().getData()[0];
		details[1] = config.getSize().getData()[1];
		
		return details;
	}
}
