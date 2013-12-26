package me.tempus.src;

import java.util.HashMap;
import java.util.Random;

import me.tempus.collada.ColladaParser;
import me.tempus.collision.AABB;
import me.tempus.gameobjects.Box;
import me.tempus.gameobjects.Geometry;
import me.tempus.shader.PVM;
import me.tempus.shader.VCI_Shader;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;


public class MainScreen {

	private boolean done = false;
	
	private VCI_Shader shader;
	
	//OpenGL Scene setup
	private final Camera camera = new Camera();
	private Geometry[] sceneGeometry;	
	private Random random = new Random();

	//State Setup
	private float reward = 0;
	
	//Agent Setup
	private Box agent;
	Vector3f agentPos = new Vector3f(0,0,0);
	private Vector3f initalAgentPos = new Vector3f(0,0,0);
	private final float agentMoveSpeed = 1f;
	private final float learnSpeed = 1f;
	private float fatigue = 1f;
	private final float fatigueLoss = 0.1f;
	private double[] weights = new double[]{1,1,1};
	private final Vector3f[] moveDirections = new Vector3f[]{
			new Vector3f(1,0,1),
			new Vector3f(-1,0,-1),
			new Vector3f(1,0,-1),
			new Vector3f(-1,0,1),
			new Vector3f(1, 0, 0),
			new Vector3f(-1, 0, 0),
			new Vector3f(0, 0, 1),
			new Vector3f(0, 0, -1)
	};
	
	//Enemy Setup
	private Box enemy;
	Vector3f enemyPos;
	private final float enemyMoveSpeed = 1f;
	private final int steps = 50;
	
	//Food Setup
	private Box food;
	private boolean beingEaten = false;

	private boolean resetPosition;

	private boolean collidingFood;

	private boolean collidingEnemy;
	
	private void createScreen(int width, int height){
		
		
		try {

			Display.setDisplayMode(new DisplayMode(width, height));
			Display.setTitle("Camera");
				Display.create();
			
			GL11.glViewport(0, 0, width, height);

			GL11.glEnable(GL11.GL_TEXTURE_2D);                          // Enable Texture Mapping
			
			GL11.glShadeModel(GL11.GL_SMOOTH);                          // Enables Smooth Color Shading
			GL11.glClearColor(0.5f, 0.5f, 0.5f, 0.0f);                // This Will Clear The Background Color To Black
			GL11.glClearDepth(1.0);                                   // Enables Clearing Of The Depth Buffer
			GL11.glEnable(GL11.GL_DEPTH_TEST);                          // Enables Depth Testing
			GL11.glDepthFunc(GL11.GL_LEQUAL);                           // The Type Of Depth Test To Do
			PVM.setUpProjection(45f, width, height, 0.1f, 100f);
			System.out.println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION));
		} catch (LWJGLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void initOPG(){
		Keyboard.enableRepeatEvents(true);
		
	}
	
	public void loop(){
		
		createScreen(640, 480);
		initOPG();
		setUpShaders();
		loadContent();
		while(!done){
			update();
			draw();
			
			
		}
		return;
	}
	
	private void loadContent() {
		//player = new Box(playerPos, new Vector3f(0,1,0));
		//player.create();
		
		final HashMap<String, me.tempus.collada.Geometry> geometryMap = (new ColladaParser()).parse("AI_Test_Scene.DAE");
		sceneGeometry = new Geometry[geometryMap.size()];
		int i = 0;
		for(me.tempus.collada.Geometry g : geometryMap.values()){
			final Box geometry = new Box();
			geometry.setName(g.getId());
			geometry.setIndices(g.getIndices());
			geometry.setVertices(g.getPosistions());
			geometry.setTriangleSize(g.getTriangleCount());
			geometry.setModelMatrix(g.getModelMatrix());
			
			geometry.setPos(g.getTranslation());
			geometry.setRotation(new float[]{g.getxRotation(), g.getyRotation(), g.getzRotation()});
			geometry.setScale(g.getScale());
			
			geometry.setColour(new Vector3f((float)random.nextDouble(), (float)random.nextDouble(), (float)random.nextDouble()));
			geometry.setInitialColour(geometry.getColour());
			
			geometry.create();
			
			if(geometry.getTriangleSize() == 0){
				System.err.println("Can't have 0 triangles");
				System.exit(1);
			}
			geometry.setIDs(shader.getVBOID(geometry.getVertices(), geometry.getIndices()));
			sceneGeometry[i] = geometry;
			i++;
		}
		
		//Agent Init
		agent = (Box) sceneGeometry[2];
		agentPos = agent.getPos();
		initalAgentPos.x = agentPos.x;
		initalAgentPos.y = agentPos.y;
		initalAgentPos.z = agentPos.z;
		
		//Enemy Init
		enemy = (Box) sceneGeometry[3];
		enemyPos = enemy.getPos();
		//enemyPos = new Vector3f(-3.940292f, 1.0f, 4.3591003f);
		enemyPos = new Vector3f(32.059708f, 1.0f, -15.6409f);
		
		//Food Init
		food = (Box) sceneGeometry[1];
	}

	private void setUpShaders(){
		shader = new VCI_Shader("shaders/VIC.vert", "shaders/VIC.frag");
	}
	
	private void update(){
		//Camera Movement
		camera.pollInput();
		PVM.loadIdentity();
		camera.transform();
		
		//while(Keyboard.next()){
			if(Keyboard.getEventKeyState()){
				if(Keyboard.getEventKey() == Keyboard.KEY_UP){
					enemyPos.z += 5;
				}else if(Keyboard.getEventKey() == Keyboard.KEY_DOWN){
					enemyPos.z -= 5;
				}
				if(Keyboard.getEventKey() == Keyboard.KEY_LEFT){
					enemyPos.x += 5;
				}else if(Keyboard.getEventKey() == Keyboard.KEY_RIGHT){
					enemyPos.x -= 5;
				}
			}
		//}
		collidingEnemy = false;
		collidingFood = false;
		 // Find actions
		final double expectedReward = findBestActions(); // This will set the agent's new position
		
		//Take actions
		agent.setPos(agentPos);
		enemy.setPos(enemyPos);
		fatigue += fatigueLoss;
		
		//Asses results
		if(AABB.intersect(agent.getAABB(), enemy.getAABB())){
			addReward(-1);
			collidingEnemy = true;
			resetPosition = true;
		}
		if(AABB.intersect(agent.getAABB(), food.getAABB())){
			addReward(1);
			collidingFood = true;
			fatigue = 1;
			resetPosition = true;
		}
		if(fatigue <= 0){
			addReward(-1);
			fatigue = 1;
			//resetPosition = true;
		}
		//Adjust's actions based on results
		final double actualReward = assesActions(weights, agentPos, enemyPos, food.getPos());
		adjustWeights(actualReward, expectedReward);
		reward = 0;
		
		if(resetPosition){
			agentPos.x = initalAgentPos.x;
			agentPos.y = initalAgentPos.y;
			agentPos.z = initalAgentPos.z;
			resetPosition = false;
		}
	}
	
	public void addReward(double reward){
		this.reward += reward;
		 
	}
	
	/**
	 * Finds the permutation for the agent's features that will return the greatest sum
	 * Set's the agent's variables to take these actions
	 */
	public double findBestActions(){
		
		
		
		int bestMoveAgentIndex = -1;
		double bestResult = Double.NaN;
		double currentResult = -1;
		for(int i = 0; i < moveDirections.length; i++){
			currentResult = assesActions(weights, addTo(agentPos, scale(moveDirections[i], agentMoveSpeed)), enemyPos, food.getPos());
			if(Double.isNaN(bestResult)){
				bestResult = currentResult;
				bestMoveAgentIndex = i;
			}
			if(currentResult > bestResult){
				bestMoveAgentIndex = i;
				bestResult = currentResult;
			}
		}
		System.out.println("Move: " + bestMoveAgentIndex);
		agentPos = addTo(agentPos, scale(moveDirections[bestMoveAgentIndex], agentMoveSpeed));
		
		return currentResult;
	}
	
	private double assesActions(double[] weights, Vector3f agentPos, Vector3f enemyPos, Vector3f foodPos){
		final double result = (weights[0] * getEnemyDistance(enemyPos, agentPos)) + (weights[1] * getFoodDistance(food.getPos(), agentPos)) + (weights[2] * getEating());
		//System.out.println("Weight result: " + result);
		return result;
	}
	
	public void adjustWeights(double actualReward, double expectedReward){
		if(Double.isNaN(actualReward)){
			actualReward = 0;
		}
		if(Double.isNaN(expectedReward)){
			expectedReward = 0;
		}
		final double correction = reward; // + (int)(expectedReward - actualReward); //Implement
		//System.out.println("Correction is: " + correction);
		/*
		 * These only need to happen if they are happening
		 * Too much precision leads to false positives
		 * Maybe simply enemy distance and food distance to if they are colliding
		 */
		int enemyMod = 0;
		int foodMod = 0;
		if(collidingEnemy){
			enemyMod = 1;
		}
		if(collidingFood){
			foodMod = 1;
		}
		weights[0] += learnSpeed * correction * enemyMod;
		weights[1] += learnSpeed * correction * foodMod;
		weights[2] += learnSpeed * correction * getEating();
	}
	
	//Agent's Features
	private double getEnemyDistance(Vector3f enemyPos, Vector3f agentPos){
		final Vector3f dif = new Vector3f(enemyPos.x - agentPos.x, enemyPos.y - agentPos.y, enemyPos.z - agentPos.z);
		final double result = (1/Math.sqrt((dif.x * dif.x) + (dif.y * dif.y) + (dif.z * dif.z))) +1;
		//System.out.println("Result: " + result);
		return result;
	}
	
	private double getFoodDistance(Vector3f foodPos, Vector3f agentPos){
		final Vector3f dif = new Vector3f(foodPos.x - agentPos.x, foodPos.y - agentPos.y, foodPos.z - agentPos.z);
		final double result = (1/Math.sqrt((dif.x * dif.x) + (dif.y * dif.y) + (dif.z * dif.z))+1);
		//System.out.println("Result: " + result);
		return result;
	}
	
	private int getEating(){
		if(AABB.intersect(agent.getAABB(), food.getAABB())){
			return 1;
		}else{
			return 0;
		}
	}
	
	/**
	 * Scales the vector, a, with b
	 * @param a
	 * @param s
	 * @return The resulting vector
	 */
	private static Vector3f scale(Vector3f a, float s){
		return new Vector3f(a.x*s, a.y*s, a.z*s);
	}
	
	/**
	 * Adds b to a
	 * a += b
	 * @param a
	 * @param b
	 */
	private static Vector3f addTo(Vector3f a, Vector3f b){
		final Vector3f result = new Vector3f(a.x, a.y, a.z);
		result.x += b.x;
		result.y += b.y;
		result.z += b.z;
		
		return result;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	private void draw(){
		
		GL20.glUseProgram(shader.getShaderID());
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		
		
		for(int i = 0; i < sceneGeometry.length; i++){
			final Geometry g = sceneGeometry[i];
			g.doTransformation();
			shader.setColour(g.getColour());
			shader.render(g.getVboID(), g.getIndicesID(), g.getTriangleSize());
		}
		
		GL20.glUseProgram(0);
		Display.sync(60);
		Display.update();
		
	}
	
	
	
	public static void main(String[] args){
		(new MainScreen()).loop();
	}
}
