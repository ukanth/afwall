package dev.ukanth.ufirewall.widget;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.util.G;

public class RadialMenuWidget extends View {

	//Defines the interface
	public interface RadialMenuEntry {
	      String getName();
	      String getLabel();
	      int getIcon();
	      List<RadialMenuEntry> getChildren();
	      void menuActiviated();
	}	

	
	private List<RadialMenuEntry> menuEntries = new ArrayList<RadialMenuEntry>();
	private RadialMenuEntry centerCircle = null;
	
	private float screen_density = getContext().getResources().getDisplayMetrics().density;
	
	private int defaultColor = Color.rgb(0, 0, 0); 	//default color of wedge pieces
	private int defaultAlpha = 180;  						//transparency of the colors, 255=Opague, 0=Transparent
	private int wedge2Color = Color.rgb(85, 85, 85); 	//default color of wedge pieces
	private int wedge2Alpha = 210; 
	private int outlineColor = Color.rgb(255, 255, 255);  	//color of outline
	private int outlineAlpha = 255;							//transparency of outline
	private int selectedColor = Color.rgb(0, 0, 0);  	//color to fill when something is selected
	private int selectedAlpha = 210;						//transparency of fill when something is selected

	private int disabledColor = Color.rgb(85, 85, 85);  	//color to fill when something is selected
	private int disabledAlpha = 100;						//transparency of fill when something is selected
	
	private int pictureAlpha = 255;							//transparency of images

	private int textColor = Color.rgb(255, 255, 255);  	//color to fill when something is selected
	private int textAlpha = 255;						//transparency of fill when something is selected

	private int headerTextColor = Color.rgb(255, 255, 255);  	//color of header text
	private int headerTextAlpha = 255;							//transparency of header text
	private int headerBackgroundColor =  Color.rgb(0, 0, 0);	//color of header background
	private int headerBackgroundAlpha =  180;					//transparency of header background
	
	private int wedgeQty = 1;				//Number of wedges
	private Wedge[] Wedges = new Wedge[wedgeQty];
	private Wedge selected = null;			//Keeps track of which wedge is selected
	private Wedge enabled = null;			//Keeps track of which wedge is enabled for outer ring
	private Rect[] iconRect = new Rect[wedgeQty];


	private int wedgeQty2 = 1;				//Number of wedges
	private Wedge[] Wedges2 = new Wedge[wedgeQty2];
	private Wedge selected2 = null;			//Keeps track of which wedge is selected
	private Rect[] iconRect2 = new Rect[wedgeQty2]; 
	private RadialMenuEntry wedge2Data = null;		//Keeps track off which menuItem data is being used for the outer ring
	
	private int MinSize = scalePX(35);				//Radius of inner ring size
	private int MaxSize = scalePX(90);				//Radius of outer ring size
	private int r2MinSize = MaxSize+scalePX(5);		//Radius of inner second ring size
	private int r2MaxSize = r2MinSize+scalePX(45);	//Radius of outer second ring size
	private int MinIconSize = scalePX(15);					//Min Size of Image in Wedge
	private int MaxIconSize = scalePX(35);			//Max Size of Image in Wedge
	//private int BitmapSize = scalePX(40);			//Size of Image in Wedge
	private int cRadius = MinSize-scalePX(7); 	 	//Inner Circle Radius
	private int textSize = scalePX(15);				//TextSize
	private int animateTextSize = textSize;
	
	private int xPosition = getSizeX();			//Center X location of Radial Menu
	private int yPosition = getSizeY();			//Center Y location of Radial Menu

	private int xSource = 0;			//Source X of clicked location
	private int ySource = 0;			//Center Y of clicked location
	private boolean showSource = false;	//Display icon where at source location
	
	private boolean inWedge = false;		//Identifies touch event was in first wedge
	private boolean inWedge2 = false;		//Identifies touch event was in second wedge
	private boolean inCircle = false;		//Identifies touch event was in middle circle

	private boolean Wedge2Shown = false;		//Identifies 2nd wedge is drawn
	private boolean HeaderBoxBounded = false;	//Identifies if header box is drawn
	
	private String headerString = null;
	private int headerTextSize = textSize;				//TextSize
	private int headerBuffer = scalePX(8);
	private Rect textRect = new Rect();
	private RectF textBoxRect = new RectF();
	private int headerTextLeft; 
	private int headerTextBottom;
	
	//private RotateAnimation rotate;
	//private AlphaAnimation blend;
	private ScaleAnimation scale; 
    private TranslateAnimation move;
    private AnimationSet spriteAnimation; 
    private long animationSpeed = 400L;
    

    private static final int ANIMATE_IN = 1;
    private static final int ANIMATE_OUT = 2;
    
    private int animateSections = 4;
    private int r2VariableSize;
	private boolean animateOuterIn = false;
	private boolean animateOuterOut = false;
	
	
	
	@SuppressLint("NewApi")
	public RadialMenuWidget(Context context) {
		super(context);

		// Gets screen specs and defaults to center of screen
		DisplayMetrics dm = new DisplayMetrics();
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(dm);

		/*Display mdisp = wm.getDefaultDisplay();

		Point mdispSize = new Point();
		mdisp.getSize(mdispSize);
		int maxX = mdispSize.x;
		int maxY = mdispSize.y; */


		this.xPosition = G.getWidgetX(context) / 2;
		this.yPosition =  G.getWidgetY(context) / 2;



		determineWedges();
		onOpenAnimation();
	}

	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int state = e.getAction();
		int eventX = (int) e.getX();
		int eventY = (int) e.getY();
		if (state == MotionEvent.ACTION_DOWN) {
			//selected = null;
			//selected2 = null;
			inWedge = false;
			inWedge2 = false;
			inCircle = false;


			//Checks if a pie slice is selected in first Wedge
			for (int i = 0; i < Wedges.length; i++) {
				Wedge f = Wedges[i];
				double slice = (2*Math.PI) / wedgeQty;
				double start = (2*Math.PI)*(0.75) - (slice/2);		//this is done so top slice is the centered on top of the circle

				inWedge = pntInWedge(eventX, eventY,
						xPosition, yPosition, 
			            MinSize, MaxSize,
			            (i* slice)+start, slice);					
				
				if (inWedge == true) {
					selected = f;
					break;
				}
			}

			
			//Checks if a pie slice is selected in second Wedge
			if (Wedge2Shown == true) {
				for (int i = 0; i < Wedges2.length; i++) {
					Wedge f = Wedges2[i];
					double slice = (2*Math.PI) / wedgeQty2;
					double start = (2*Math.PI)*(0.75) - (slice/2);		//this is done so top slice is the centered on top of the circle
	
					inWedge2 = pntInWedge(eventX, eventY,
				            xPosition, yPosition, 
				            r2MinSize, r2MaxSize,
				            (i* slice)+start, slice);					
					
					if (inWedge2 == true) {
						selected2 = f;
						break;
					}
				}

			}
			
			//Checks if center circle is selected
			inCircle = pntInCircle(eventX, eventY, xPosition,yPosition,cRadius);

		} else if (state == MotionEvent.ACTION_UP) {
			//execute commands...
			//put in stuff here to "return" the button that was pressed.
			if (inCircle == true) {  
				if (Wedge2Shown == true) {
					enabled = null;
					animateOuterIn = true;  //sets Wedge2Shown = false;
				}
				selected = null;
				//Toast.makeText(getContext(), centerCircle.getName() + " pressed.", Toast.LENGTH_SHORT).show();
				centerCircle.menuActiviated();

			} else if (selected != null){
				for (int i = 0; i < Wedges.length; i++) {
					Wedge f = Wedges[i];
					if (f == selected) {
						
						//Checks if a inner ring is enabled if so closes the outer ring an
						if (enabled != null) {
							//Toast.makeText(getContext(), "Closing outer ring", Toast.LENGTH_SHORT).show();
							enabled = null;
							animateOuterIn = true;  //sets Wedge2Shown = false;
							
						//If outer ring is not enabled, then executes event
						} else {
							//Toast.makeText(getContext(), menuEntries.get(i).getName() + " pressed.", Toast.LENGTH_SHORT).show();
							menuEntries.get(i).menuActiviated();
							
							//Figures out how many outer rings
							if (menuEntries.get(i).getChildren() != null) {
								determineOuterWedges(menuEntries.get(i));
								enabled = f;
								animateOuterOut = true;  //sets Wedge2Shown = true;m
								
							} else {
								Wedge2Shown = false;
							}
							
						}
						selected = null;
					}					
				}
			} else if (selected2 != null){
				for (int i = 0; i < Wedges2.length; i++) {
					Wedge f = Wedges2[i];
					if (f == selected2) {
						//Toast.makeText(getContext(), wedge2Data.getChildren().get(i).getName() + " pressed.", Toast.LENGTH_SHORT).show();
						animateOuterIn = true;  //sets Wedge2Shown = false;
						wedge2Data.getChildren().get(i).menuActiviated();
						enabled = null;
						selected = null;
					}					
				}
			} else {
				//This is when something outside the circle or any of the rings is selected
				//selected = null;
				//enabled = null;
			}
			//selected = null;
			selected2 = null;
			inCircle = false;
		}
		invalidate();
		return true;
	}


	@Override
    protected void onDraw(Canvas c) {

		
		Paint paint = new Paint();
    	paint.setAntiAlias(true);
    	paint.setStrokeWidth(3);

    	// draws a dot at the source of the press
    	if (showSource == true ) {
			paint.setColor(outlineColor);
	    	paint.setAlpha(outlineAlpha); 
			paint.setStyle(Paint.Style.STROKE);
			c.drawCircle(xSource, ySource, cRadius/10, paint);
	
		    paint.setColor(selectedColor);
	    	paint.setAlpha(selectedAlpha);
	    	paint.setStyle(Paint.Style.FILL);
	    	c.drawCircle(xSource, ySource, cRadius/10, paint);
    	}
    	
   	
 		for (int i = 0; i < Wedges.length; i++) {
			Wedge f = Wedges[i];
	    	paint.setColor(outlineColor);
	    	paint.setAlpha(outlineAlpha); 
			paint.setStyle(Paint.Style.STROKE);
	    	c.drawPath(f, paint);
			if (f == enabled && Wedge2Shown == true) {
		    	paint.setColor(wedge2Color);
		    	paint.setAlpha(wedge2Alpha);
		    	paint.setStyle(Paint.Style.FILL);
		    	c.drawPath(f, paint);
			} else if (f != enabled && Wedge2Shown == true) {
		    	paint.setColor(disabledColor);
		    	paint.setAlpha(disabledAlpha);
		    	paint.setStyle(Paint.Style.FILL);
		    	c.drawPath(f, paint);			
			} else if (f == enabled && Wedge2Shown == false) {
		    	paint.setColor(wedge2Color);
		    	paint.setAlpha(wedge2Alpha);
		    	paint.setStyle(Paint.Style.FILL);
		    	c.drawPath(f, paint);
			} else if (f == selected) {
		    	paint.setColor(wedge2Color);
		    	paint.setAlpha(wedge2Alpha);
		    	paint.setStyle(Paint.Style.FILL);
		    	c.drawPath(f, paint);	
		    } else {
		    	paint.setColor(defaultColor);
		    	paint.setAlpha(defaultAlpha);
		    	paint.setStyle(Paint.Style.FILL);
		    	c.drawPath(f, paint);
			}

			Rect rf = iconRect[i];

			if ((menuEntries.get(i).getIcon() != 0) && (menuEntries.get(i).getLabel() != null)) {
				
				//This will look for a "new line" and split into multiple lines					
				String menuItemName = menuEntries.get(i).getLabel();
				String[] stringArray = menuItemName.split("\n");

		    	paint.setColor(textColor);
				if (f != enabled && Wedge2Shown == true) {
			    	paint.setAlpha(disabledAlpha);
				} else {
					paint.setAlpha(textAlpha);
				}
				paint.setStyle(Paint.Style.FILL);
				paint.setTextSize(textSize);
				
				Rect rect = new Rect();
				float textHeight = 0;
				for (int j = 0; j < stringArray.length; j++) 
			    	{
					paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
					textHeight = textHeight+(rect.height()+3);
			    	}

				Rect rf2 = new Rect();
				rf2.set(rf.left, rf.top-((int)textHeight/2), rf.right, rf.bottom-((int)textHeight/2));

				float textBottom = rf2.bottom;
				for (int j = 0; j < stringArray.length; j++) 
			    	{
					paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
					float textLeft = rf.centerX() - rect.width()/2;
					textBottom = textBottom + (rect.height()+3);
					c.drawText(stringArray[j], textLeft-rect.left, textBottom-rect.bottom, paint);
			    	}
							
				//Puts in the Icon
			    Drawable drawable = getResources().getDrawable(menuEntries.get(i).getIcon());			    
				drawable.setBounds(rf2);
				if (f != enabled && Wedge2Shown == true) {
					drawable.setAlpha(disabledAlpha);
				} else {
					drawable.setAlpha(pictureAlpha);
				}
				drawable.draw(c);					

		//Icon Only
			} else if (menuEntries.get(i).getIcon() != 0) {
				//Puts in the Icon
			    Drawable drawable = getResources().getDrawable(menuEntries.get(i).getIcon());			    
				drawable.setBounds(rf);
				if (f != enabled && Wedge2Shown == true) {
					drawable.setAlpha(disabledAlpha);
				} else {
					drawable.setAlpha(pictureAlpha);
				}
				drawable.draw(c);				
				
				
		//Text Only					
			} else {
				//Puts in the Text if no Icon
		    	paint.setColor(textColor);
				if (f != enabled && Wedge2Shown == true) {
			    	paint.setAlpha(disabledAlpha);
				} else {
					paint.setAlpha(textAlpha);
				}
				paint.setStyle(Paint.Style.FILL);
				paint.setTextSize(textSize);
				
				//This will look for a "new line" and split into multiple lines
				String menuItemName = menuEntries.get(i).getLabel();
				String[] stringArray = menuItemName.split("\n");

				//gets total height
				Rect rect = new Rect();
				float textHeight = 0;
				for (int j = 0; j < stringArray.length; j++) 
			    	{
					paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
					textHeight = textHeight+(rect.height()+3);
			    	}

				float textBottom = rf.centerY()-(textHeight/2);
				for (int j = 0; j < stringArray.length; j++) 
			    	{
					paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
					float textLeft = rf.centerX() - rect.width()/2;
					textBottom = textBottom + (rect.height()+3);
					c.drawText(stringArray[j], textLeft-rect.left, textBottom-rect.bottom, paint);
			    	}								
			}			
			
		}

		
		//Animate the outer ring in/out
		if (animateOuterIn == true) {
			animateOuterWedges(ANIMATE_IN);
		}
		else if (animateOuterOut == true) {
			animateOuterWedges(ANIMATE_OUT);
		}			
		
		if (Wedge2Shown == true) {
			
			for (int i = 0; i < Wedges2.length; i++) {
				Wedge f = Wedges2[i];
		    	paint.setColor(outlineColor);
		    	paint.setAlpha(outlineAlpha); 
				paint.setStyle(Paint.Style.STROKE);
		    	c.drawPath(f, paint);
				if (f == selected2) {
			    	paint.setColor(selectedColor);
			    	paint.setAlpha(selectedAlpha);
			    	paint.setStyle(Paint.Style.FILL);
			    	c.drawPath(f, paint);
				} else {
			    	paint.setColor(wedge2Color);
			    	paint.setAlpha(wedge2Alpha);
			    	paint.setStyle(Paint.Style.FILL);
			    	c.drawPath(f, paint);
				}
	
				Rect rf = iconRect2[i];
				if(wedge2Data.getChildren().size() > 0) {
					if ((wedge2Data.getChildren().get(i).getIcon() != 0) && (wedge2Data.getChildren().get(i).getLabel() != null)) {

						//This will look for a "new line" and split into multiple lines
						String menuItemName = wedge2Data.getChildren().get(i).getLabel();
						String[] stringArray = menuItemName.split("\n");

						paint.setColor(textColor);
						paint.setAlpha(textAlpha);
						paint.setStyle(Paint.Style.FILL);
						paint.setTextSize(animateTextSize);

						Rect rect = new Rect();
						float textHeight = 0;
						for (int j = 0; j < stringArray.length; j++)
						{
							paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
							textHeight = textHeight+(rect.height()+3);
						}

						Rect rf2 = new Rect();
						rf2.set(rf.left, rf.top-((int)textHeight/2), rf.right, rf.bottom-((int)textHeight/2));

						float textBottom = rf2.bottom;
						for (int j = 0; j < stringArray.length; j++)
						{
							paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
							float textLeft = rf.centerX() - rect.width()/2;
							textBottom = textBottom + (rect.height()+3);
							c.drawText(stringArray[j], textLeft-rect.left, textBottom-rect.bottom, paint);
						}


						//Puts in the Icon
						Drawable drawable = getResources().getDrawable(wedge2Data.getChildren().get(i).getIcon());
						drawable.setBounds(rf2);
						drawable.setAlpha(pictureAlpha);
						drawable.draw(c);

						//Icon Only
					} else if (wedge2Data.getChildren().get(i).getIcon() != 0) {
						//Puts in the Icon
						Drawable drawable = getResources().getDrawable(wedge2Data.getChildren().get(i).getIcon());
						drawable.setBounds(rf);
						drawable.setAlpha(pictureAlpha);
						drawable.draw(c);

						//Text Only
					} else {
						//Puts in the Text if no Icon
						paint.setColor(textColor);
						paint.setAlpha(textAlpha);
						paint.setStyle(Paint.Style.FILL);
						paint.setTextSize(animateTextSize);

						//This will look for a "new line" and split into multiple lines
						String menuItemName = wedge2Data.getChildren().get(i).getLabel();
						String[] stringArray = menuItemName.split("\n");

						//gets total height
						Rect rect = new Rect();
						float textHeight = 0;
						for (int j = 0; j < stringArray.length; j++)
						{
							paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
							textHeight = textHeight+(rect.height()+3);
						}

						float textBottom = rf.centerY()-(textHeight/2);
						for (int j = 0; j < stringArray.length; j++)
						{
							paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
							float textLeft = rf.centerX() - rect.width()/2;
							textBottom = textBottom + (rect.height()+3);
							c.drawText(stringArray[j], textLeft-rect.left, textBottom-rect.bottom, paint);
						}
					}
				}

			}
		}
		
		//Draws the Middle Circle
		paint.setColor(outlineColor);
    	paint.setAlpha(outlineAlpha); 
		paint.setStyle(Paint.Style.STROKE);
	    c.drawCircle(xPosition, yPosition, cRadius, paint);
		if (inCircle == true) {
		    paint.setColor(selectedColor);
	    	paint.setAlpha(selectedAlpha);
	    	paint.setStyle(Paint.Style.FILL);
		    c.drawCircle(xPosition, yPosition, cRadius, paint);
		    onCloseAnimation(); 
		} else {
	    	paint.setColor(defaultColor);
	    	paint.setAlpha(defaultAlpha);
	    	paint.setStyle(Paint.Style.FILL);
		    c.drawCircle(xPosition, yPosition, cRadius, paint);
		}		
		
		
	    // Draw the circle picture
		if ((centerCircle.getIcon() != 0) && (centerCircle.getLabel() != null)) {

			//This will look for a "new line" and split into multiple lines					
			String menuItemName = centerCircle.getLabel();
			String[] stringArray = menuItemName.split("\n");

	    	paint.setColor(textColor);
	    	paint.setAlpha(textAlpha); 
			paint.setStyle(Paint.Style.FILL);
			paint.setTextSize(textSize);

			Rect rectText = new Rect();
			Rect rectIcon = new Rect();
		    Drawable drawable = getResources().getDrawable(centerCircle.getIcon());

		    int h = getIconSize(drawable.getIntrinsicHeight(),MinIconSize,MaxIconSize);
		    int w = getIconSize(drawable.getIntrinsicWidth(),MinIconSize,MaxIconSize);		    
		    rectIcon.set(xPosition-w/2, yPosition-h/2, xPosition+w/2, yPosition+h/2);			
			

			float textHeight = 0;
			for (int j = 0; j < stringArray.length; j++) 
		    	{
				paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rectText);
				textHeight = textHeight+(rectText.height()+3);
		    	}

			rectIcon.set(rectIcon.left, rectIcon.top-((int)textHeight/2), rectIcon.right, rectIcon.bottom-((int)textHeight/2));

			float textBottom = rectIcon.bottom;
			for (int j = 0; j < stringArray.length; j++) 
		    	{
				paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rectText);
				float textLeft = xPosition - rectText.width()/2;
				textBottom = textBottom + (rectText.height()+3);
				c.drawText(stringArray[j], textLeft-rectText.left, textBottom-rectText.bottom, paint);
		    	}
			
			
			//Puts in the Icon
			drawable.setBounds(rectIcon);
			drawable.setAlpha(pictureAlpha);
			drawable.draw(c);					

		//Icon Only
		} else if (centerCircle.getIcon() != 0) {
			
			Rect rect = new Rect();
			
		    Drawable drawable = getResources().getDrawable(centerCircle.getIcon());

		    int h = getIconSize(drawable.getIntrinsicHeight(),MinIconSize,MaxIconSize);
		    int w = getIconSize(drawable.getIntrinsicWidth(),MinIconSize,MaxIconSize);		    
		    rect.set(xPosition-w/2, yPosition-h/2, xPosition+w/2, yPosition+h/2);
		    
			drawable.setBounds(rect);
			drawable.setAlpha(pictureAlpha);
			drawable.draw(c);

		//Text Only				
		} else {
			//Puts in the Text if no Icon
	    	paint.setColor(textColor);
	    	paint.setAlpha(textAlpha); 
			paint.setStyle(Paint.Style.FILL);
			paint.setTextSize(textSize);
			
			//This will look for a "new line" and split into multiple lines
			String menuItemName = centerCircle.getLabel();
			String[] stringArray = menuItemName.split("\n");

			//gets total height
			Rect rect = new Rect();
			float textHeight = 0;
			for (int j = 0; j < stringArray.length; j++) 
		    	{
				paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
				textHeight = textHeight+(rect.height()+3);
		    	}
			
			float textBottom = yPosition-(textHeight/2);
			for (int j = 0; j < stringArray.length; j++) 
		    	{
				paint.getTextBounds(stringArray[j],0,stringArray[j].length(),rect);
				float textLeft = xPosition - rect.width()/2;
				textBottom = textBottom + (rect.height()+3);
				c.drawText(stringArray[j], textLeft-rect.left, textBottom-rect.bottom, paint);
		    	}				
							
			
		}

		// Draws Text in TextBox
		if (headerString != null) {

    		paint.setTextSize(headerTextSize);
    		paint.getTextBounds(headerString,0,headerString.length(),this.textRect);
	    	if (HeaderBoxBounded == false) {
	    		determineHeaderBox();
	    		HeaderBoxBounded = true;
	    	}
	    	
        	paint.setColor(outlineColor);
        	paint.setAlpha(outlineAlpha); 
    		paint.setStyle(Paint.Style.STROKE);
    		c.drawRoundRect(this.textBoxRect, scalePX(5), scalePX(5), paint);		
        	paint.setColor(headerBackgroundColor);
        	paint.setAlpha(headerBackgroundAlpha); 
    		paint.setStyle(Paint.Style.FILL);
    		c.drawRoundRect(this.textBoxRect, scalePX(5), scalePX(5), paint);
    		
        	paint.setColor(headerTextColor);
        	paint.setAlpha(headerTextAlpha); 
    		paint.setStyle(Paint.Style.FILL);
    		paint.setTextSize(headerTextSize);
    		c.drawText(headerString, headerTextLeft, headerTextBottom, paint);
		}

	}

	
	private int getIconSize(int iconSize, int minSize, int maxSize) {
		
	    if (iconSize > minSize) {
	    	if (iconSize > maxSize) {
	    		return maxSize;
	    	} else {	//iconSize < maxSize
	    		return iconSize;
	    	}
	    } else {  //iconSize < minSize
	    	return minSize;
	    }

	}
	
	
	
    private void onOpenAnimation() { 

    	//rotate = new RotateAnimation(0, 360, xPosition, yPosition);
        //rotate.setRepeatMode(Animation.REVERSE); 
        //rotate.setRepeatCount(Animation.INFINITE); 
        scale = new ScaleAnimation(0, 1, 0, 1, xPosition, yPosition); 
        //scale.setRepeatMode(Animation.REVERSE); 
        //scale.setRepeatCount(Animation.INFINITE); 
        scale.setInterpolator(new DecelerateInterpolator());
        move = new TranslateAnimation(xSource-xPosition, 0, ySource-yPosition, 0);
 
        spriteAnimation = new AnimationSet(true); 
        //spriteAnimation.addAnimation(rotate); 
        spriteAnimation.addAnimation(scale); 
        spriteAnimation.addAnimation(move);
        spriteAnimation.setDuration(animationSpeed); 
 
        startAnimation(spriteAnimation); 
 
    } 
    private void onCloseAnimation() { 
    	
        //rotate = new RotateAnimation(360, 0, xPosition, yPosition);
        scale = new ScaleAnimation(1, 0, 1, 0, xPosition, yPosition); 
        scale.setInterpolator(new AccelerateInterpolator());
        move = new TranslateAnimation(0, xSource-xPosition, 0, ySource-yPosition);
 
        spriteAnimation = new AnimationSet(true); 
        //spriteAnimation.addAnimation(rotate); 
        spriteAnimation.addAnimation(scale); 
        spriteAnimation.addAnimation(move);
        spriteAnimation.setDuration(animationSpeed); 
 
        startAnimation(spriteAnimation); 
 
    } 	
	
    private boolean pntInCircle(double px, double py, double x1, double y1, double radius) {
		double diffX = x1 - px;
		double diffY = y1 - py;
		double dist = diffX*diffX + diffY*diffY;
		return dist < radius*radius;
	}
	
	 
    private boolean pntInWedge(double px, double py, 
            float xRadiusCenter, float yRadiusCenter, 
            int innerRadius, int outerRadius,
            double startAngle, double sweepAngle) {
    	double diffX = px-xRadiusCenter;
		double diffY = py-yRadiusCenter;
		
		double angle = Math.atan2(diffY,diffX);
		if (angle < 0)
		  angle += (2*Math.PI);

		if (startAngle >= (2*Math.PI)) {
			startAngle = startAngle-(2*Math.PI);
		}
		
		//checks if point falls between the start and end of the wedge
		if ((angle >= startAngle && angle <= startAngle + sweepAngle) ||
				(angle+(2*Math.PI) >= startAngle && (angle+(2*Math.PI)) <= startAngle + sweepAngle)) {
		    
			// checks if point falls inside the radius of the wedge
			double dist = diffX*diffX + diffY*diffY;
			return dist < outerRadius * outerRadius && dist > innerRadius * innerRadius;
			}
		  return false;
		}

    
    public boolean addMenuEntry( RadialMenuEntry entry )
    {
       menuEntries.add( entry );
	   determineWedges();
       return true;
    }

    public boolean setCenterCircle( RadialMenuEntry entry )
    {
       centerCircle = entry;
       return true;
    }
    
    
    public void setInnerRingRadius( int InnerRadius, int OuterRadius )
    {
       this.MinSize = scalePX(InnerRadius);
       this.MaxSize = scalePX(OuterRadius);
       determineWedges();
    }    
    
    public void setOuterRingRadius( int InnerRadius, int OuterRadius )
    {
       this.r2MinSize = scalePX(InnerRadius);
       this.r2MaxSize = scalePX(OuterRadius);     
       determineWedges();
    }    

    public void setCenterCircleRadius( int centerRadius )
    {
       this.cRadius = scalePX(centerRadius);    
       determineWedges();
    }     
    
    public void setTextSize( int TextSize )
    {
       this.textSize = scalePX(TextSize); 
       this.animateTextSize = this.textSize;
    }     
    
    public void setIconSize( int minIconSize, int maxIconSize )
    {
       this.MinIconSize = scalePX(minIconSize);
       this.MaxIconSize = scalePX(maxIconSize);
       determineWedges();
    }    
    

    public void setCenterLocation( int x, int y )
    {
       this.xPosition = x;
       this.yPosition = y;
       determineWedges();
       onOpenAnimation();
    }        

    public void setSourceLocation( int x, int y )
    {
       this.xSource = x;
       this.ySource = y; 
       onOpenAnimation();
    }      

    public void setShowSourceLocation( boolean showSourceLocation )
    {
       this.showSource = showSourceLocation;
       onOpenAnimation();
    }
    
    public void setAnimationSpeed( long millis )
    {
       this.animationSpeed = millis;
       onOpenAnimation();
    }   
    
    public void setInnerRingColor( int color, int alpha )
    {
       this.defaultColor = color;
       this.defaultAlpha = alpha; 
    }     
    public void setOuterRingColor( int color, int alpha )
    {
       this.wedge2Color = color;
       this.wedge2Alpha = alpha; 
    }       
    public void setOutlineColor( int color, int alpha )
    {
       this.outlineColor = color;
       this.outlineAlpha = alpha; 
    }    
    public void setSelectedColor( int color, int alpha )
    {
       this.selectedColor = color;
       this.selectedAlpha = alpha; 
    }       
    
    public void setDisabledColor( int color, int alpha )
    {
       this.disabledColor = color;
       this.disabledAlpha = alpha; 
    }       
    
    public void setTextColor( int color, int alpha )
    {
       this.textColor = color;
       this.textAlpha = alpha; 
    }      
 
    public void setHeader( String header, int TextSize )
    {
    	this.headerString = header;
    	this.headerTextSize = scalePX(TextSize);
    	HeaderBoxBounded = false;
    }    
    public void setHeaderColors( int TextColor, int TextAlpha, int BgColor, int BgAlpha )
    {
    	this.headerTextColor = TextColor; 
    	this.headerTextAlpha = TextAlpha;
    	this.headerBackgroundColor =  BgColor;
    	this.headerBackgroundAlpha =  BgAlpha;

    }      

    
    private int scalePX( int dp_size )
    {
		int px_size = (int) (dp_size * screen_density + 0.5f);
        return px_size;
    }

	private int getSizeX()
	{
		DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
		Float f = displayMetrics.widthPixels / displayMetrics.density;
		return f.intValue();
	}

	private int getSizeY()
	{
		DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
		Float f = displayMetrics.heightPixels / displayMetrics.density;
		return f.intValue();
	}
    
    
   private void animateOuterWedges( int animation_direction) { 

	   	boolean animationComplete = false;
	   	
	   
		//Wedge 2
	    float slice2 = 360 / wedgeQty2;
		float start_slice2 = 270 - (slice2/2);
    	//calculates where to put the images
		double rSlice2 = (2*Math.PI) / wedgeQty2;
		double rStart2 = (2*Math.PI)*(0.75) - (rSlice2/2);		
		
		this.Wedges2 = new Wedge[wedgeQty2];
		this.iconRect2 = new Rect[wedgeQty2];
		
    	Wedge2Shown = true;
		
    	int wedgeSizeChange = (r2MaxSize-r2MinSize)/animateSections;
    	
	    if (animation_direction==ANIMATE_OUT) {
	        if ( r2MinSize+r2VariableSize+wedgeSizeChange < r2MaxSize) {
	        	r2VariableSize += wedgeSizeChange;
	        } else {
	        	animateOuterOut = false;
	        	r2VariableSize = r2MaxSize - r2MinSize;
	        	animationComplete = true;
	        }
	        
	        //animates text size change
	        this.animateTextSize = (textSize/animateSections) * (r2VariableSize/wedgeSizeChange);
	        
	        //calculates new wedge sizes
			for (int i = 0; i < Wedges2.length; i++) {
				this.Wedges2[i] = new Wedge(xPosition, yPosition, r2MinSize, r2MinSize+r2VariableSize, (i
						* slice2)+start_slice2, slice2);
				float xCenter = (float)(Math.cos(((rSlice2*i)+(rSlice2*0.5))+rStart2) * (r2MinSize+r2VariableSize+r2MinSize)/2)+xPosition;
				float yCenter = (float)(Math.sin(((rSlice2*i)+(rSlice2*0.5))+rStart2) * (r2MinSize+r2VariableSize+r2MinSize)/2)+yPosition;

				int h = MaxIconSize;
				int w = MaxIconSize;
				if(wedge2Data.getChildren().size() > 0) {
					if ( wedge2Data.getChildren().get(i).getIcon() != 0 ) {
						Drawable drawable = getResources().getDrawable(wedge2Data.getChildren().get(i).getIcon());
						h = getIconSize(drawable.getIntrinsicHeight(),MinIconSize,MaxIconSize);
						w = getIconSize(drawable.getIntrinsicWidth(),MinIconSize,MaxIconSize);
					}
				}


				if (r2VariableSize < h) {
					h = r2VariableSize;
				}				
				if (r2VariableSize < w) {
					w = r2VariableSize;
				}					
				
			    this.iconRect2[i] = new Rect((int) xCenter-w/2, (int) yCenter-h/2, (int) xCenter+w/2, (int) yCenter+h/2);
				

				int widthOffset = MaxSize;
				if (widthOffset < this.textRect.width()/2) {
					widthOffset = this.textRect.width()/2+scalePX(3);
				}
				this.textBoxRect.set((xPosition - (widthOffset)),
						yPosition - (r2MinSize+r2VariableSize) - headerBuffer-this.textRect.height()-scalePX(3),
						(xPosition + (widthOffset)), 
						(yPosition - (r2MinSize+r2VariableSize) - headerBuffer+scalePX(3)));
				this.headerTextBottom = yPosition - (r2MinSize+r2VariableSize) - headerBuffer-this.textRect.bottom;		
			    
			}

	    }
	    else if (animation_direction==ANIMATE_IN) {
	        if ( r2MinSize < r2MaxSize-r2VariableSize-wedgeSizeChange) {
	        	r2VariableSize += wedgeSizeChange;
	        } else {
	        	animateOuterIn = false;
	        	r2VariableSize = r2MaxSize;
	        	animationComplete = true;
	        }
	        
	        //animates text size change
	        this.animateTextSize = textSize - ((textSize/animateSections) * (r2VariableSize/wedgeSizeChange));

	        
			for (int i = 0; i < Wedges2.length; i++) {
				this.Wedges2[i] = new Wedge(xPosition, yPosition, r2MinSize, r2MaxSize-r2VariableSize, (i
						* slice2)+start_slice2, slice2);
				
				float xCenter = (float)(Math.cos(((rSlice2*i)+(rSlice2*0.5))+rStart2) * (r2MaxSize-r2VariableSize+r2MinSize)/2)+xPosition;
				float yCenter = (float)(Math.sin(((rSlice2*i)+(rSlice2*0.5))+rStart2) * (r2MaxSize-r2VariableSize+r2MinSize)/2)+yPosition;

				int h = MaxIconSize;
				int w = MaxIconSize;
				if ( wedge2Data.getChildren().size() > 0 && wedge2Data.getChildren().get(i).getIcon() != 0 ) {
				    Drawable drawable = getResources().getDrawable(wedge2Data.getChildren().get(i).getIcon());
				    h = getIconSize(drawable.getIntrinsicHeight(),MinIconSize,MaxIconSize);
				    w = getIconSize(drawable.getIntrinsicWidth(),MinIconSize,MaxIconSize);		
				}

				if (r2MaxSize-r2MinSize-r2VariableSize < h) {
					h = r2MaxSize-r2MinSize-r2VariableSize;
				}				
				if (r2MaxSize-r2MinSize-r2VariableSize < w) {
					w = r2MaxSize-r2MinSize-r2VariableSize;
				}					
				
			    this.iconRect2[i] = new Rect((int) xCenter-w/2, (int) yCenter-h/2, (int) xCenter+w/2, (int) yCenter+h/2);
				
		
			    //computes header text box
		    	int heightOffset = r2MaxSize-r2VariableSize;	
				int widthOffset = MaxSize;
		    	if (MaxSize > r2MaxSize-r2VariableSize) {heightOffset = MaxSize;} 
				if (widthOffset < this.textRect.width()/2) {
					widthOffset = this.textRect.width()/2+scalePX(3);
				}
				this.textBoxRect.set((xPosition - (widthOffset)),
						yPosition - (heightOffset) - headerBuffer-this.textRect.height()-scalePX(3),
						(xPosition + (widthOffset)), 
						(yPosition - (heightOffset) - headerBuffer+scalePX(3)));
				this.headerTextBottom = yPosition - (heightOffset) - headerBuffer-this.textRect.bottom;		

			}
	    }
 		
	    if (animationComplete == true) {
	    	r2VariableSize = 0;
	    	this.animateTextSize = textSize;
	    	if (animation_direction==ANIMATE_IN) {
	    		Wedge2Shown = false;
	    	}
	    }
	    
		invalidate();  //re-draws the picture
  } 	    
   
   private void determineWedges() { 

	    int entriesQty = menuEntries.size();
	    if ( entriesQty > 0) {
		    wedgeQty = entriesQty;
		    
		    float degSlice = 360 / wedgeQty;
			float start_degSlice = 270 - (degSlice/2);
	    	//calculates where to put the images
			double rSlice = (2*Math.PI) / wedgeQty;
			double rStart = (2*Math.PI)*(0.75) - (rSlice/2);		
			
			this.Wedges = new Wedge[wedgeQty];
			this.iconRect = new Rect[wedgeQty];
					
			for (int i = 0; i < Wedges.length; i++) {
				this.Wedges[i] = new Wedge(xPosition, yPosition, MinSize, MaxSize, (i
						* degSlice)+start_degSlice, degSlice);
				float xCenter = (float)(Math.cos(((rSlice*i)+(rSlice*0.5))+rStart) * (MaxSize+MinSize)/2)+xPosition;
				float yCenter = (float)(Math.sin(((rSlice*i)+(rSlice*0.5))+rStart) * (MaxSize+MinSize)/2)+yPosition;
				
				int h = MaxIconSize;
				int w = MaxIconSize;
				if ( menuEntries.get(i).getIcon() != 0 ) {
				    Drawable drawable = getResources().getDrawable(menuEntries.get(i).getIcon());
				    h = getIconSize(drawable.getIntrinsicHeight(),MinIconSize,MaxIconSize);
				    w = getIconSize(drawable.getIntrinsicWidth(),MinIconSize,MaxIconSize);
				}
				
			    this.iconRect[i] = new Rect( (int) xCenter-w/2, (int) yCenter-h/2, (int) xCenter+w/2, (int) yCenter+h/2);
			}
			
			invalidate();  //re-draws the picture
	    }
   } 	    
   
   private void determineOuterWedges(RadialMenuEntry entry) { 

	    int entriesQty = entry.getChildren().size();
	    wedgeQty2 = entriesQty;

	   //if only default profile
	   if(entriesQty == 0 ) {
		   wedgeQty2 = 1;
	   }
		//Wedge 2
	    float degSlice2 = 360 / wedgeQty2;
		float start_degSlice2 = 270 - (degSlice2/2);
   	//calculates where to put the images
		double rSlice2 = (2*Math.PI) / wedgeQty2;
		double rStart2 = (2*Math.PI)*(0.75) - (rSlice2/2);		
		
		this.Wedges2 = new Wedge[wedgeQty2];
		this.iconRect2 = new Rect[wedgeQty2];
				
		for (int i = 0; i < Wedges2.length; i++) {
			this.Wedges2[i] = new Wedge(xPosition, yPosition, r2MinSize, r2MaxSize, (i
					* degSlice2)+start_degSlice2, degSlice2);
			float xCenter = (float)(Math.cos(((rSlice2*i)+(rSlice2*0.5))+rStart2) * (r2MaxSize+r2MinSize)/2)+xPosition;
			float yCenter = (float)(Math.sin(((rSlice2*i)+(rSlice2*0.5))+rStart2) * (r2MaxSize+r2MinSize)/2)+yPosition;

			int h = MaxIconSize;
			int w = MaxIconSize;
			if(wedgeQty2 > 1) {
				if ( entry.getChildren().get(i).getIcon() != 0 ) {
					Drawable drawable = getResources().getDrawable(entry.getChildren().get(i).getIcon());
					h = getIconSize(drawable.getIntrinsicHeight(),MinIconSize,MaxIconSize);
					w = getIconSize(drawable.getIntrinsicWidth(),MinIconSize,MaxIconSize);
					this.iconRect2[i] = new Rect((int) xCenter-w/2, (int) yCenter-h/2, (int) xCenter+w/2, (int) yCenter+h/2);
				}
			}
		}
		this.wedge2Data = entry;
		invalidate();  //re-draws the picture
  } 	    
   
   private void determineHeaderBox() { 

		this.headerTextLeft = xPosition - this.textRect.width()/2;
		this.headerTextBottom = yPosition - (MaxSize) - headerBuffer-this.textRect.bottom;
		int offset = MaxSize;
		if (offset < this.textRect.width()/2) {
			offset = this.textRect.width()/2+scalePX(3);
		}
		
		this.textBoxRect.set((xPosition - (offset)),
				yPosition - (MaxSize) - headerBuffer-this.textRect.height()-scalePX(3),
				(xPosition + (offset)), 
				(yPosition - (MaxSize) - headerBuffer+scalePX(3)));
	
	}
   
    public class Wedge extends Path {
    	private int x, y;
    	private int InnerSize, OuterSize;
    	private float StartArc;
    	private float ArcWidth;
    	
    	private Wedge(int x, int y, int InnerSize, int OuterSize, float StartArc, float ArcWidth) {
    		super();
    		
    		if (StartArc >= 360) {
    			StartArc = StartArc-360;
    		}
    		
    		this.x = x; this.y = y;
    		this.InnerSize = InnerSize;
    		this.OuterSize = OuterSize;
    		this.StartArc = StartArc;
    		this.ArcWidth = ArcWidth;
    		this.buildPath();
    	}
    	
    	private void buildPath() {

    	    final RectF rect = new RectF();
    	    final RectF rect2 = new RectF();
    	    
    	    //Rectangles values
    	    rect.set(this.x-this.InnerSize, this.y-this.InnerSize, this.x+this.InnerSize, this.y+this.InnerSize);
    	    rect2.set(this.x-this.OuterSize, this.y-this.OuterSize, this.x+this.OuterSize, this.y+this.OuterSize);
    	   		
    		this.reset();
    		//this.moveTo(100, 100);
    		this.arcTo(rect2, StartArc, ArcWidth);
    		this.arcTo(rect, StartArc+ArcWidth, -ArcWidth);
    				
    		this.close();


    	}
    }

}
