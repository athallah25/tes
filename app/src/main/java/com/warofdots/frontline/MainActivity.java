package com.warofdots.frontline;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512 | 4096);
        setContentView(new GameView(this));
    }

    static final int BLUE=0, RED=1, INFANTRY=0, TANK=1, AIR=2;
    static final int[] COST={60,130,160};
    static final String[] UNIT_NAME={"INFANTRY","TANK","AIR WING"};
    static final float[] HP={48,150,78}, SPEED={62,34,92}, RANGE={58,90,118}, DAMAGE={9,25,19}, COOLDOWN={.75f,1.15f,.65f};
    static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    static float dist(float x,float y,float a,float b){return (float)Math.hypot(a-x,b-y);}

    static class Unit {
        float x,y,hp,cd,tx,ty; int team,type; boolean selected,hasOrder;
        Unit(float x,float y,int team,int type){this.x=x;this.y=y;this.team=team;this.type=type;hp=HP[type];tx=x;ty=y;}
    }
    static class Base {
        float x,y,hp=900; int team;
        Base(float x,float y,int t){this.x=x;this.y=y;team=t;}
    }
    static class Point {
        float x,y,progress; int owner=-1; final int id;
        Point(float x,float y,int id){this.x=x;this.y=y;this.id=id;}
    }

    class GameView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random random=new Random();
        final ArrayList<Unit> units=new ArrayList<>();
        final ArrayList<Base> bases=new ArrayList<>();
        final ArrayList<Point> points=new ArrayList<>();
        Canvas canvas; float sx,sy,W,H; long last=System.currentTimeMillis();
        int credits=300, enemyCredits=300, selectedType=0, ai=0;
        float spawn=0, income=0, aiClock=0, matchTime=0;
        boolean paused=false, ended=false, victory=false;
        Unit selected=null; String message="Select units, then tap ground to issue orders.";
        RectF[] production=new RectF[3]; RectF aiButton,pauseButton,restartButton;
        int bg=Color.rgb(15,27,34), grid=Color.rgb(28,44,51);
        int blue=Color.rgb(70,177,255), red=Color.rgb(255,91,105), mint=Color.rgb(85,220,181), gold=Color.rgb(255,202,92);
        GameView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);restart();}
        void restart(){
            units.clear();bases.clear();points.clear();selected=null;credits=enemyCredits=300;
            bases.add(new Base(95,450,BLUE));bases.add(new Base(1405,450,RED));
            points.add(new Point(520,280,0));points.add(new Point(750,600,1));points.add(new Point(980,300,2));
            for(int i=0;i<5;i++) units.add(new Unit(180+random.nextInt(70),365+random.nextInt(170),BLUE,INFANTRY));
            for(int i=0;i<5;i++) units.add(new Unit(1250+random.nextInt(70),365+random.nextInt(170),RED,INFANTRY));
            units.add(new Unit(260,430,BLUE,TANK));units.add(new Unit(1240,470,RED,TANK));
            units.add(new Unit(260,340,BLUE,AIR));units.add(new Unit(1240,560,RED,AIR));
            for(Unit u:units) if(u.team==BLUE){u.tx=u.x;u.ty=u.y;}
            paused=ended=victory=false;spawn=income=aiClock=matchTime=0;
            message="Capture the three neutral points for bonus income.";last=System.currentTimeMillis();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);canvas=c;W=getWidth();H=getHeight();sx=W/1500f;sy=H/900f;
            background(); long now=System.currentTimeMillis();float dt=Math.min(.05f,(now-last)/1000f);last=now;
            if(!paused&&!ended) update(dt);
            drawTerritory();drawPoints();drawBases();drawUnits();drawHUD();
            if(paused)overlay("PAUSED","Tap RESUME to continue");
            if(ended)overlay(victory?"VICTORY":"DEFEAT", "Tap NEW BATTLE to restart");
            postInvalidateDelayed(30);
        }
        void fill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);}
        void stroke(int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);}
        void txt(String s,float x,float y,float size,int color,boolean bold){
            fill(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));canvas.drawText(s,x,y,p);
        }
        void background(){
            canvas.drawColor(bg);stroke(grid,1);
            for(int x=0;x<1500;x+=60)canvas.drawLine(x*sx,0,x*sx,H,p);
            for(int y=0;y<900;y+=60)canvas.drawLine(0,y*sy,W,y*sy,p);
            fill(Color.rgb(22,39,40));canvas.drawRect(0,380*sy,W,520*sy,p);
            // Decorative terrain and roads
            stroke(Color.rgb(35,57,51),13*Math.min(sx,sy));
            canvas.drawLine(300*sx,100*sy,620*sx,800*sy,p);canvas.drawLine(890*sx,80*sy,1190*sx,820*sy,p);
            fill(Color.rgb(34,58,48));
            for(int i=0;i<32;i++){float x=((i*173+49)%1450+25)*sx,y=((i*97+31)%850+25)*sy;canvas.drawCircle(x,y,4*Math.min(sx,sy),p);}
        }
        void drawTerritory(){
            fill(0x203DB1FF);canvas.drawRect(0,0,500*sx,H,p);
            fill(0x20FF5969);canvas.drawRect(1000*sx,0,W,H,p);
            stroke(0x667C9CA3,2);canvas.drawLine(750*sx,0,750*sx,H,p);
        }
        void drawPoints(){
            for(Point pt:points){
                float x=pt.x*sx,y=pt.y*sy,r=27*Math.min(sx,sy);
                int col=pt.owner==BLUE?blue:pt.owner==RED?red:gold;
                stroke(col,3);canvas.drawCircle(x,y,r,p);canvas.drawCircle(x,y,r*.62f,p);
                fill(col);canvas.drawCircle(x,y,5*Math.min(sx,sy),p);
                txt(pt.owner==-1?"NEUTRAL":pt.owner==BLUE?"BLUE":"RED",x-25*Math.min(sx,sy),y-r-7,11*Math.min(sx,sy),col,true);
            }
        }
        void drawBases(){
            for(Base b:bases){
                float x=b.x*sx,y=b.y*sy,s=Math.min(sx,sy),r=40*s;int col=b.team==BLUE?blue:red;
                fill(col);canvas.drawRoundRect(x-r,y-r,x+r,y+r,9*s,9*s,p);
                fill(Color.rgb(12,22,28));canvas.drawRoundRect(x-r*.57f,y-r*.57f,x+r*.57f,y+r*.57f,6*s,6*s,p);
                txt("HQ",x-12*s,y+5*s,17*s,Color.WHITE,true);
                fill(Color.DKGRAY);canvas.drawRoundRect(x-r,y+r+8*s,x+r,y+r+16*s,3*s,3*s,p);
                fill(mint);canvas.drawRoundRect(x-r,y+r+8*s,x-r+2*r*clamp(b.hp/900f,0,1),y+r+16*s,3*s,3*s,p);
            }
        }
        void drawUnits(){
            for(Unit u:units){
                float x=u.x*sx,y=u.y*sy,s=Math.min(sx,sy);int col=u.team==BLUE?blue:red;
                if(u.selected){stroke(gold,2.5f*s);canvas.drawCircle(x,y,19*s,p);}
                if(u.type==INFANTRY){fill(col);canvas.drawCircle(x,y,8*s,p);fill(Color.WHITE);canvas.drawCircle(x,y,2*s,p);}
                else if(u.type==TANK){fill(col);canvas.drawRoundRect(x-14*s,y-9*s,x+14*s,y+9*s,4*s,4*s,p);stroke(Color.WHITE,1.4f*s);canvas.drawLine(x-2*s,y,x+13*s,y,p);}
                else {fill(col);Path path=new Path();path.moveTo(x+15*s,y);path.lineTo(x-9*s,y-9*s);path.lineTo(x-4*s,y);path.lineTo(x-9*s,y+9*s);path.close();canvas.drawPath(path,p);stroke(Color.WHITE,1*s);canvas.drawLine(x-5*s,y,x+8*s,y,p);}
                fill(Color.DKGRAY);canvas.drawRect(x-12*s,y-15*s,x+12*s,y-12*s,p);
                fill(mint);canvas.drawRect(x-12*s,y-15*s,x-12*s+24*s*clamp(u.hp/HP[u.type],0,1),y-12*s,p);
            }
        }
        void drawHUD(){
            float s=Math.min(sx,sy);fill(0xEA101D25);canvas.drawRect(0,0,W,85*s,p);
            txt("WAR OF DOTS: FRONTLINE",18*s,27*s,21*s,Color.WHITE,true);
            txt("CREDITS "+credits,18*s,57*s,15*s,gold,true);
            txt("HQ "+(int)bases.get(0).hp+" HP",160*s,57*s,14*s,blue,true);
            txt("ENEMY HQ "+(int)bases.get(1).hp+" HP",260*s,57*s,14*s,red,true);
            txt("CAPTURED "+captured(BLUE)+"/3",400*s,57*s,14*s,mint,true);
            txt(ai==0?"SMART AI":"DIVINELY SMART AI",Math.max(650*s,W-390*s),27*s,16*s,ai==0?mint:gold,true);
            txt(message,Math.max(650*s,W-520*s),55*s,11*s,Color.LTGRAY,false);
            float y=H-62*s,bw=110*s,bh=46*s,x=16*s,g=7*s;
            for(int i=0;i<3;i++){production[i]=new RectF(x+i*(bw+g),y,x+i*(bw+g)+bw,y+bh);button(production[i],UNIT_NAME[i],"$"+COST[i],selectedType==i?0xFF287AA4:0xFF354B55);}
            float rx=W-324*s;aiButton=new RectF(rx,y,rx+139*s,y+bh);button(aiButton,ai==0?"AI: SMART":"AI: DIVINE","TOGGLE",ai==0?0xFF354B55:0xFF71521C);
            pauseButton=new RectF(rx+146*s,y,rx+224*s,y+bh);button(pauseButton,paused?"RESUME":"PAUSE","",0xFF354B55);
            restartButton=new RectF(W-80*s,10*s,W-13*s,43*s);button(restartButton,"NEW","",0xFF783642);
            txt("Tap unit to select • tap battlefield to move • buy units with credits",16*s,H-72*s,11*s,Color.LTGRAY,false);
        }
        void button(RectF r,String a,String b,int col){float s=Math.min(sx,sy);fill(col);canvas.drawRoundRect(r,8*s,8*s,p);txt(a,r.left+7*s,r.top+18*s,12*s,Color.WHITE,true);if(!b.isEmpty())txt(b,r.left+7*s,r.top+35*s,10*s,gold,true);}
        void overlay(String title,String sub){fill(0xCC071016);canvas.drawRect(0,0,W,H,p);float s=Math.min(sx,sy);txt(title,W*.5f-title.length()*13*s,H*.46f,40*s,title.equals("VICTORY")?mint:Color.WHITE,true);txt(sub,W*.5f-140*s,H*.54f,15*s,Color.LTGRAY,false);}
        int captured(int team){int n=0;for(Point pt:points)if(pt.owner==team)n++;return n;}
        void update(float dt){
            matchTime+=dt;spawn+=dt;income+=dt;aiClock+=dt;
            if(income>=2){credits+=2+captured(BLUE)*3;enemyCredits+=2+captured(RED)*3;income=0;}
            if(spawn>=1.4f){
                if(enemyCredits>=COST[ai==1?1:0]){int type=enemyChoose();enemyCredits-=COST[type];units.add(new Unit(133+1270,400+random.nextInt(100),RED,type));}
                spawn=0;
            }
            if(aiClock>(ai==0?2.4f:0.85f)){aiPlan();aiClock=0;}
            // Capture points by local troop presence.
            for(Point pt:points){
                int b=0,r=0;for(Unit u:units)if(dist(u.x,u.y,pt.x,pt.y)<75){if(u.team==BLUE)b++;else r++;}
                if(b>r&&b>0){pt.progress=clamp(pt.progress+dt*(.12f+b*.035f),-1,1);if(pt.progress>=1)pt.owner=BLUE;}
                else if(r>b&&r>0){pt.progress=clamp(pt.progress-dt*(.12f+r*.035f),-1,1);if(pt.progress<=-1)pt.owner=RED;}
                else if(b==0&&r==0){pt.progress*=Math.max(0,1-dt*.04f);}
            }
            ArrayList<Unit> dead=new ArrayList<>();
            for(Unit u:units){
                Unit enemy=nearestEnemy(u);
                Base enemyBase=bases.get(1-u.team);
                float tx,ty;
                if(u.team==BLUE&&u.selected&&u.hasOrder){tx=u.tx;ty=u.ty;}
                else if(enemy!=null&&dist(u.x,u.y,enemy.x,enemy.y)<RANGE[u.type]*1.35f){tx=u.x;ty=u.y;}
                else if(u.team==RED&&ai==1){Point goal=bestPointForAI();if(goal!=null){tx=goal.x;ty=goal.y;}else{tx=enemyBase.x;ty=enemyBase.y;}}
                else {tx=enemyBase.x;ty=enemyBase.y;}
                float d=dist(u.x,u.y,tx,ty);
                if(d>RANGE[u.type]*.78f&&d>2){u.x+=(tx-u.x)/d*SPEED[u.type]*dt;u.y+=(ty-u.y)/d*SPEED[u.type]*dt;}
                u.x=clamp(u.x,30,1470);u.y=clamp(u.y,100,820);
                u.cd-=dt;
                if(u.cd<=0){
                    Unit target=null;float bd=Float.MAX_VALUE;
                    for(Unit v:units)if(v.team!=u.team){
                        float dd=dist(u.x,u.y,v.x,v.y);
                        // Aircraft are more effective against aircraft; tanks favor ground units.
                        if(dd<=RANGE[u.type]&&dd<bd){bd=dd;target=v;}
                    }
                    if(target!=null){float damage=DAMAGE[u.type];if(u.type==TANK&&target.type==AIR)damage*=.55f;if(u.type==AIR&&target.type==AIR)damage*=1.35f;target.hp-=damage;u.cd=COOLDOWN[u.type];}
                    else if(dist(u.x,u.y,enemyBase.x,enemyBase.y)<RANGE[u.type]+36){enemyBase.hp-=DAMAGE[u.type]*.42f;u.cd=COOLDOWN[u.type];}
                }
                if(u.hp<=0)dead.add(u);
            }
            units.removeAll(dead);
            if(bases.get(0).hp<=0||bases.get(1).hp<=0){ended=true;victory=bases.get(1).hp<=0;message=victory?"Enemy HQ destroyed!":"Your HQ was destroyed.";}
        }
        Point bestPointForAI(){
            Point best=null;float score=Float.MAX_VALUE;
            for(Point pt:points){float s=dist(1350,450,pt.x,pt.y)+(pt.owner==RED?450:0);if(s<score){score=s;best=pt;}}
            return best;
        }
        int enemyChoose(){
            if(ai==1){
                int air=0,tanks=0;for(Unit u:units)if(u.team==BLUE){if(u.type==AIR)air++;if(u.type==TANK)tanks++;}
                if(air>tanks&&enemyCredits>=COST[AIR])return AIR;
                if(tanks>air&&enemyCredits>=COST[TANK])return TANK;
                if(enemyCredits>=COST[TANK]&&random.nextFloat()<.58f)return TANK;
                if(enemyCredits>=COST[AIR]&&random.nextFloat()<.4f)return AIR;
            }
            if(enemyCredits>=COST[TANK]&&random.nextFloat()<.28f)return TANK;
            return INFANTRY;
        }
        void aiPlan(){
            for(Unit u:units)if(u.team==RED){
                Unit target=nearestEnemy(u);
                if(ai==1&&target!=null&&target.hp<HP[target.type]*.5f){u.tx=target.x;u.ty=target.y;u.hasOrder=true;}
                else {Point pt=bestPointForAI();if(pt!=null){u.tx=pt.x;u.ty=pt.y;u.hasOrder=true;}}
            }
            message=ai==0?"Smart AI: expanding and attacking.":"Divinely Smart AI: counter-building and contesting objectives.";
        }
        Unit nearestEnemy(Unit u){
            Unit best=null;float bd=Float.MAX_VALUE;
            for(Unit v:units)if(v.team!=u.team){
                float d=dist(u.x,u.y,v.x,v.y);
                if(ai==1&&u.team==RED&&v.hp<HP[v.type]*.5f)d*=.75f;
                if(d<bd){bd=d;best=v;}
            }
            return best;
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY(),s=Math.min(sx,sy);
            if(restartButton!=null&&restartButton.contains(x,y)){restart();return true;}
            if(aiButton!=null&&aiButton.contains(x,y)){ai=1-ai;message=ai==0?"Smart AI selected.":"Divinely Smart AI selected.";return true;}
            if(pauseButton!=null&&pauseButton.contains(x,y)){paused=!paused;return true;}
            for(int i=0;i<3;i++)if(production[i]!=null&&production[i].contains(x,y)){
                selectedType=i;if(credits>=COST[i]){credits-=COST[i];units.add(new Unit(150+random.nextInt(45),400+random.nextInt(90),BLUE,i));message=UNIT_NAME[i]+" deployed.";}
                else message="Not enough credits for "+UNIT_NAME[i]+".";
                return true;
            }
            if(ended)return true;
            float wx=x/sx,wy=y/sy;
            Unit tapped=null;float bd=Float.MAX_VALUE;
            for(Unit u:units)if(u.team==BLUE){float d=dist(wx,wy,u.x,u.y);if(d<26&&d<bd){bd=d;tapped=u;}}
            if(tapped!=null){
                if(selected!=null)selected.selected=false;
                selected=tapped;tapped.selected=true;tapped.hasOrder=false;message=UNIT_NAME[tapped.type]+" selected. Tap destination.";
            } else if(y>85*s&&y<H-68*s){
                if(selected!=null){selected.tx=wx;selected.ty=wy;selected.hasOrder=true;message="Move order issued.";}
                else message="Select one of your units first.";
            }
            return true;
        }
    }
}
