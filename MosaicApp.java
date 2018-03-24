import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
/*
 * James "Riley" Jackson
 * 300200062
 * Done "almost" entirely with JavaFx * 
 * - Uses flamingo image Placed in \\src\\flamingo.jpg
 * - writes mosaic file to \\src\\mosaic.jpg
 * - uses images provided placed in \\src\\jpg
 */
public class MosaicApp extends Application{
	
	//TODO: Make mosaic of girlfriend out of slugs or something.
	
	final static String baseImagePath = "flamingo.jpg";//has to be in same path as class
	final static String tileImagesPath = "src\\jpg\\";
	final static String mosaicImagePath = "src\\rileyMosaic.jpg";
	
	final static int tileWidth = 80;
	final static int tileHeight = 60;
	
	public static void main(String[] args){
		launch(args);
	}
	
	public static void saveImage(Image img, File f){
	    //Sadly I had to use a buffered image here.. =(
		try {
	            ImageIO.write(SwingFXUtils.fromFXImage(img, null),
	                    "png", f);
	        } catch (IOException ex) {
	            System.out.println(ex.getMessage());
	        }
	}
	
	// creates the task used to load the images. Can be performed on another thread to keep UI responsive, which allows progress bar!.
	// Didn't try running multiple tasks on different threads as Disk bottlenecks using only 1 .. sometimes
	private static Task<Tile[]> createTask_LoadTiles() throws Exception {

		return new Task<Tile[]>() {

			@Override
			public Tile[] call() throws Exception {									
				File[] files = new File(tileImagesPath).listFiles(); //load up the image directory
				if(files == null) throw new Exception("Tile Images folder not Found at " + tileImagesPath);
				
				Tile[] tiles = new Tile[files.length];//stores all the images, and determines there average rgb
				updateMessage("Found " + tiles.length + " files");
				
				//Iterate through all the files in the directory
				for (int i = 0; i < files.length; i++) {
					try{
					Image img = new Image("jpg\\" + files[i].getName()); //load file as image
					tiles[i] = new Tile(img); // create a tile from that image
					updateProgress(i, tiles.length);
					}
					catch(Exception e){ //sometimes the files arn't an image, or other things happen
						System.err.println("Problem loading: " + files[i].getName());
						System.err.println("Does image folder: " + tileImagesPath + " contain files not in an image format?");
						throw e;
					}
				}
				return tiles;
			}
			
		};
	}
		
	/*
	 * Non Static i.e. The Application
	 */

	private Tile[] tileArray;
	private Image baseImage;
	private Image mosaic;
	private ImageView mainView;

	@Override
	public void start(Stage stage) throws Exception {
			
		//Load Startup Image
		baseImage = new Image(baseImagePath, false);
		mainView = new ImageView(baseImage);
		mainView.setFitHeight(700);
		mainView.setFitWidth(700);
		
		//Create Mosaic button
		Button mosaicButton = new Button("Create Mosaic!");
		mosaicButton.setOnAction( e-> mosaicButton_ClickHandler() );
		mosaicButton.setDisable(true);
		
		//Create HBox For Bottom Of Application
		HBox bHBox = new HBox();
		bHBox.getChildren().add(mosaicButton);
		bHBox.setPadding( new Insets(0,0,10,10) );		
		
		//Create BorderPane For Organizing Other Panes
		BorderPane pane = new BorderPane();
		pane.setCenter(mainView);
		pane.setBottom(bHBox);
		pane.setStyle("-fx-background-color: #000000;");
		
		//Setup and Show Stage
		stage.setScene(new Scene(pane,800,800));
		stage.setTitle("Mosaic Creator");
		stage.show();
		
		//Setup a Task to load the Tile Images
		ProgressWindow progressWindow = new ProgressWindow();		
		Task<Tile[]> loadImgTsk = createTask_LoadTiles();
		
		loadImgTsk.setOnSucceeded(e -> {
			tileArray = loadImgTsk.getValue();
			progressWindow.close();
			mosaicButton.setDisable(false);
		});
		
		//So we can see exceptions thrown in separate thread
		loadImgTsk.exceptionProperty().addListener((observable, oldValue, newValue) ->{
			  if(newValue != null) {
				    Exception ex = (Exception) newValue;
				    ex.printStackTrace();
				  }	
			  System.exit(0);
			});
		
		progressWindow.getStatusProperty().bind(loadImgTsk.messageProperty());
		progressWindow.getProgressProperty().bind(loadImgTsk.progressProperty());
		
		//Create a Thread to run the task
		Thread loadingThread = new Thread(loadImgTsk);
		loadingThread.setDaemon(true);
		
		//Start Loading tileImages
		progressWindow.open();
		loadingThread.start();		
	}
	
	private void mosaicButton_ClickHandler(){
		// Mosaic image is created here
		
		//design stores the baseImage as an array of Tile. baseImage tiles can now be matched against tiles in tileArray
		//Original baseImage tile is overwritten with best matching tileArray tile, then design is converted back into an image 
		Tile[][] design = splitBaseImageIntoTiles(); 		
		boolean[] usedTile = new boolean[tileArray.length]; //keeps track of used tiles, identified by [tileArrid]
		
		//Iterate through design, populating it with matching tiles.
		for(int y = 0; y < design.length; y++){
			for(int x = 0; x < design[0].length; x++){ 				
				int bestID = -1; //stores tileArray position of best match
				double bestScore = -1;	
				for(int i = 0; i < tileArray.length; i++){ //iterate through entire list of tiles to find best match
					if(usedTile[i]) continue;					
					Tile t = tileArray[i];
					double score = 0;
					//Tiles are split into quadrants, each quadrant is matched against opposing quadrant
					for(int yQ = 0; yQ < t.quadrant.length; yQ++){ //.length allows scaling if one wanted to use more sections than 4 
						for(int xQ = 0; xQ < t.quadrant[0].length; xQ++){
							score += design[y][x].quadrant[yQ][xQ].scoreAgainst( t.quadrant[yQ][xQ] );
						}
					}					
					if(bestScore == -1 || score < bestScore){//low score == better
						bestScore = score;
						bestID = i;
					}
				}
				usedTile[bestID] = true;
				design[y][x] = tileArray[bestID];
			}
		}
		
		mosaic = createImageFromTiles(design);
		mainView.setImage(mosaic);
		
		File f = new File(mosaicImagePath);		
		saveImage(mosaic, f);		
	}
	
	private Tile[][] splitBaseImageIntoTiles(){						
		int width = (int) baseImage.getHeight();
		int height = (int) baseImage.getWidth();
		Tile[][] out = new Tile[height/tileHeight][width/tileWidth];
		PixelReader pr = baseImage.getPixelReader();
		//Iterate through each tile we need to make
		//Note: this part could be simplified with imageview.setViewport(2D Rectangle) then imageView.snapshot(null, null).
		for(int  y = 0; y < height/tileHeight; y++){			
			for(int x = 0; x < width/tileWidth; x++){				
				WritableImage template = new WritableImage(tileWidth, tileHeight);
				PixelWriter pw = template.getPixelWriter();
				//copy each pixel representing the current tile from base Image and write it to a template tile 
				for(int y2 = 0; y2 < tileHeight; y2++){
					for(int x2 = 0; x2 < tileWidth; x2++){						
						int argb = pr.getArgb(x*tileWidth + x2,y*tileHeight + y2); //copy argb from absolute position
						pw.setArgb(x2, y2, argb); //write argb to relative position 					
					}					
				}				
				out[y][x] = new Tile(template); //add the newly copied tile to the tile array
			}
		}
		return out;
	}
	
	private Image createImageFromTiles(Tile[][] template){
		WritableImage img = new WritableImage( template[0].length *tileWidth, template.length * tileHeight);
		PixelWriter writer = img.getPixelWriter();
		//For every tile in template
		for(int y = 0; y < template.length; y++){
			for(int x = 0; x < template[0].length; x++){				
				PixelReader pr = template[y][x].image.getPixelReader();
				//Draw the tile using x and y of Tile[y][x]
				//Iterate through each pixel in the template tile writing them to the large final image 
				for(int y2 = 0; y2 < tileHeight; y2++){
					for(int x2 = 0; x2 < tileWidth; x2++){
						int argb = pr.getArgb(x2, y2);
						writer.setArgb(tileWidth*x + x2,tileHeight*y + y2, argb);
					}
				}
			}
		}
		return img;
	}
	

}
class Section { // Tiles are split into sections (e.g. quadrants) which store the average R G and B
	// Higher weight makes colour contribute more to total score. 1,1,1 gives decent result
	final static double rWeight = 1;
	final static double gWeight = 1;
	final static double bWeight = 1;
	
	//Laziness
	public int meanRed; 
	public int meanGreen;
	public int meanBlue;
	
	public Section(){
		meanRed = 0;
		meanGreen = 0;
		meanBlue = 0;
	}

	//allows this section to be scored against another section using euclidean distance (low score == more similar)
	public double scoreAgainst(Section s) {
		int rdif = meanRed - s.meanRed;
		int gdif = meanGreen - s.meanGreen;
		int bdif = meanBlue - s.meanBlue;
		rdif*= rWeight; gdif*= gWeight; bdif*= bWeight;
		double score = Math.sqrt( rdif*rdif + gdif*gdif + bdif*bdif);
		return score;
	}
}
class Tile 
{		    
    public Section[][] quadrant;
    final Image image;
        
    //Constructor receives an image, splits it into sections, and calculates it's average
    Tile(Image i){
        image = i;
        quadrant = new Section[2][2];        
        createSections();
    }
    
    private void createSections(){
    	int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int sectHeight = height / quadrant.length;
        int sectWidth = width / quadrant[0].length;
        
        for(int y = 0; y < quadrant.length; y++){ //should scale if something other than quadrants are used
        	for(int x = 0; x < quadrant[0].length; x++){
        		quadrant[y][x] = calculateAverages(x*sectWidth, y*sectHeight, x*sectWidth + sectWidth, y*sectHeight + sectHeight);
        	}
        }
    }
    
    private Section calculateAverages(int startX, int startY, int endX, int endY){
        int width          = (int) image.getWidth();
        int height         = (int) image.getHeight();
        PixelReader pr = image.getPixelReader();           
        
        //Add up total red, green, blue of each pixel
        long red = 0, green = 0, blue = 0;
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                int argb = pr.getArgb(x, y); // could use colour class as well
                //ignore alpha which is stored at leftmost position of integer
                red += (argb >> 16) & 0x000000FF;
                green += (argb >> 8) & 0x000000FF; 
                blue += (argb) & 0x000000FF;
            }
        }
        
        Section sect = new Section();
        int numPixels = width * height;
        sect.meanRed = (int)(red / numPixels);
        sect.meanGreen = (int)(green / numPixels);
        sect.meanBlue = (int)(blue / numPixels);
        return sect;
    }   
}
//Used to show progress of image loading
class ProgressWindow {
	private ProgressBar pb;
	private Label status;
	private Stage s;
	
	public ProgressWindow(){
		pb = new ProgressBar(0);
		status = new Label("Status:");
		Label l2 = new Label("Loading ...");
		VBox vb = new VBox();
		vb.getChildren().addAll(status, l2, pb);
		vb.setAlignment(Pos.CENTER);
		s = new Stage();
		s.setScene(new Scene(vb, 110, 80));
		s.setTitle("Loading...");
	}
	
	public void open(){
		s.show();
	}
	public void close(){
		s.close();
	}
	//properties needed by task to update the bar and text
	public StringProperty getStatusProperty(){
		return status.textProperty();
	}
	public DoubleProperty getProgressProperty(){
		return pb.progressProperty();
	}
}
