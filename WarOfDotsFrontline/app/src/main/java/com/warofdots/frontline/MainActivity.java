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
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(new GameView(this));
    }

    static final int BLUE=0, RED=1, INFANTRY=0, TANK=1, AIR=2, FIGHTER=3, BOMBER=4, TRANSPORT=5;
    static final int[] COST={60,130,160,190,220,200};
    static final String[] UNIT_NAME={"INFANTRY","TANK","AIR WING","FIGHTER","BOMBER","TRANSPORT"};
    static final float[] HP={48,150,78,85,115,100};
    static final float[] SPEED={62,34,92,112,72,82};
    static final float[] RANGE={58,90,118,135,125,48};
    static final float[] DAMAGE={9,25,19,24,38,2};
    static final float[] COOLDOWN={.75f,1.15f,.65f,.52f,1.25f,1.1f};
    static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    static float dist(float x,float y,float a,float b){return (float)Math.hypot(a-x,b-y);}
    static int teamColor(int team){return team==BLUE?Color.rgb(70,177,255):Color.rgb(255,91,105);}

    static class Unit {
        float x,y,hp,cd,tx,ty,fuel=100;
        int team,type; boolean selected,hasOrder,retreating,deployed;
        Unit(float x,float y,int team,int type){this.x=x;this.y=y;this.team=team;this.type=type;hp=HP[type];tx=x;ty=y;}
        boolean aircraft(){return type>=AIR;}
        boolean combatAircraft(){return type==AIR||type==FIGHTER||type==BOMBER;}
    }
    static class Base {
        float x,y,hp=900; int team;
        Base(float x,float y,int t){this.x=x;this.y=y;team=t;}
    }
    static class Point {
        float x,y,progress; int owner=-1; final int id;
        Point(float x,float y,int id){this.x=x;this.y=y;this.id=id;}
    }
    static class Building {
        float x,y,hp,maxHp;int team,type; // 0 barracks,1 factory,2 airfield,3 turret,4 power,5 radar
        Building(float x,float y,int team,int type){this.x=x;this.y=y;this.team=team;this.type=type;
            maxHp=type==3?240:190;hp=maxHp;}
    }

    class GameView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random random=new Random();
        final ArrayList<Unit> units=new ArrayList<>();
        final ArrayList<Base> bases=new ArrayList<>();
        final ArrayList<Point> points=new ArrayList<>();
        final ArrayList<Building> buildings=new ArrayList<>();
        Canvas canvas; float sx,sy,W,H; long last=System.currentTimeMillis();
        int credits=360,enemyCredits=360,selectedType=0,ai=1,gameMode=0,mission=0,commandMode=0;
        float spawn=0,income=0,aiClock=0,matchTime=0,dragX,dragY,dragNowX,dragNowY;
        boolean paused=false,ended=false,victory=false,menu=true,dragging=false;
        Unit selected=null; String message="Select units; drag to select a squad.";
        RectF[] production=new RectF[6];RectF[] buildButtons=new RectF[6];
        RectF aiButton,pauseButton,restartButton,commandButton,modeButton,menuStart,menuMode,menuAi,menuMission;
        int bg=Color.rgb(15,27,34),grid=Color.rgb(28,44,51);
        int blue=Color.rgb(70,177,255),red=Color.rgb(255,91,105),mint=Color.rgb(85,220,181),gold=Color.rgb(255,202,92);
        final String[] buildingNames={"BARRACKS","FACTORY","AIRFIELD","TURRET","POWER","RADAR"};
        final int[] buildingCost={140,190,210,160,130,175};
        final String[] missionNames={"OPERATION FIRST LIGHT","HOLD THE CROSSROADS","DESTROY THE RED HQ"};
        GameView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);restart();}
        void restart(){
            units.clear();bases.clear();points.clear();buildings.clear();selected=null;
            credits=enemyCredits=360;
            bases.add(new Base(95,450,BLUE));bases.add(new Base(1405,450,RED));
            points.add(new Point(520,280,0));points.add(new Point(750,600,1));points.add(new Point(980,300,2));
            for(int i=0;i<5;i++)units.add(new Unit(180+random.nextInt(70),365+random.nextInt(170),BLUE,INFANTRY));
            for(int i=0;i<5;i++)units.add(new Unit(1250+random.nextInt(70),365+random.nextInt(170),RED,INFANTRY));
            units.add(new Unit(260,430,BLUE,TANK));units.add(new Unit(1240,470,RED,TANK));
            units.add(new Unit(260,340,BLUE,AIR));units.add(new Unit(1240,560,RED,AIR));
            units.add(new Unit(220,510,BLUE,FIGHTER));units.add(new Unit(1280,350,RED,FIGHTER));
            buildings.add(new Building(170,520,BLUE,0));buildings.add(new Building(1330,380,RED,0));
            paused=ended=victory=false;spawn=income=aiClock=matchTime=0;commandMode=0;
            menu=false;message="Capture the three neutral points for bonus income.";last=System.currentTimeMillis();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);canvas=c;W=getWidth();H=getHeight();sx=W/1500f;sy=H/900f;
            background();
            long now=System.currentTimeMillis();float dt=Math.min(.05f,(now-last)/1000f);last=now;
            if(!menu&&!paused&&!ended)update(dt);
            if(menu){drawMenu();postInvalidateDelayed(50);return;}
            drawTerritory();drawPoints();drawBuildings();drawBases();drawUnits();drawFog();drawHUD();
            if(dragging){stroke(gold,2);canvas.drawRect(dragX,dragY,dragNowX,dragNowY,p);}
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
            stroke(Color.rgb(35,57,51),13*Math.min(sx,sy));
            canvas.drawLine(300*sx,100*sy,620*sx,800*sy,p);canvas.drawLine(890*sx,80*sy,1190*sx,820*sy,p);
            fill(Color.rgb(34,58,48));
            for(int i=0;i<32;i++){float x=((i*173+49)%1450+25)*sx,y=((i*97+31)%850+25)*sy;canvas.drawCircle(x,y,4*Math.min(sx,sy),p);}
        }
        void drawTerritory(){
            fill(0x203DB1FF);canvas.drawRect(0,0,500*sx,H,p);fill(0x20FF5969);canvas.drawRect(1000*sx,0,W,H,p);
            stroke(0x667C9CA3,2);canvas.drawLine(750*sx,0,750*sx,H,p);
        }
        void drawPoints(){
            for(Point pt:points){
                float x=pt.x*sx,y=pt.y*sy,r=27*Math.min(sx,sy);int col=pt.owner==BLUE?blue:pt.owner==RED?red:gold;
                stroke(col,3);canvas.drawCircle(x,y,r,p);canvas.drawCircle(x,y,r*.62f,p);fill(col);canvas.drawCircle(x,y,5*Math.min(sx,sy),p);
                txt(pt.owner==-1?"NEUTRAL":pt.owner==BLUE?"BLUE":"RED",x-25*Math.min(sx,sy),y-r-7,11*Math.min(sx,sy),col,true);
            }
        }
        void drawBuildings(){
            for(Building b:buildings){
                float x=b.x*sx,y=b.y*sy,s=Math.min(sx,sy),r=b.type==3?18*s:22*s;int col=teamColor(b.team);
                fill(col);canvas.drawRoundRect(x-r,y-r,x+r,y+r,4*s,4*s,p);
                fill(Color.rgb(13,24,29));
                if(b.type==3){Path path=new Path();path.moveTo(x,y-r*.65f);path.lineTo(x+r*.65f,y+r*.6f);path.lineTo(x-r*.65f,y+r*.6f);path.close();canvas.drawPath(path,p);}
                else canvas.drawRect(x-r*.55f,y-r*.55f,x+r*.55f,y+r*.55f,p);
                fill(Color.DKGRAY);canvas.drawRect(x-r,y+r+4*s,x+r,y+r+8*s,p);
                fill(mint);canvas.drawRect(x-r,y+r+4*s,x-r+2*r*clamp(b.hp/b.maxHp,0,1),y+r+8*s,p);
            }
        }
        void drawBases(){
            for(Base b:bases){
                float x=b.x*sx,y=b.y*sy,s=Math.min(sx,sy),r=40*s;int col=teamColor(b.team);
                fill(col);canvas.drawRoundRect(x-r,y-r,x+r,y+r,9*s,9*s,p);fill(Color.rgb(12,22,28));
                canvas.drawRoundRect(x-r*.57f,y-r*.57f,x+r*.57f,y+r*.57f,6*s,6*s,p);
                txt("HQ",x-12*s,y+5*s,17*s,Color.WHITE,true);fill(Color.DKGRAY);
                canvas.drawRoundRect(x-r,y+r+8*s,x+r,y+r+16*s,3*s,3*s,p);fill(mint);
                canvas.drawRoundRect(x-r,y+r+8*s,x-r+2*r*clamp(b.hp/900f,0,1),y+r+16*s,3*s,3*s,p);
            }
        }
        void drawUnits(){
            for(Unit u:units){
                if(u.team==RED&&!visibleToPlayer(u.x,u.y))continue;
                float x=u.x*sx,y=u.y*sy,s=Math.min(sx,sy);int col=teamColor(u.team);
                if(u.selected){stroke(gold,2.5f*s);canvas.drawCircle(x,y,19*s,p);}
                if(u.type==INFANTRY){fill(col);canvas.drawCircle(x,y,8*s,p);fill(Color.WHITE);canvas.drawCircle(x,y,2*s,p);}
                else if(u.type==TANK){fill(col);canvas.drawRoundRect(x-14*s,y-9*s,x+14*s,y+9*s,4*s,4*s,p);stroke(Color.WHITE,1.4f*s);canvas.drawLine(x-2*s,y,x+13*s,y,p);}
                else {fill(col);Path path=new Path();path.moveTo(x+15*s,y);path.lineTo(x-9*s,y-9*s);path.lineTo(x-4*s,y);path.lineTo(x-9*s,y+9*s);path.close();canvas.drawPath(path,p);stroke(Color.WHITE,1*s);canvas.drawLine(x-5*s,y,x+8*s,y,p);}
                fill(Color.DKGRAY);canvas.drawRect(x-12*s,y-15*s,x+12*s,y-12*s,p);fill(mint);
                canvas.drawRect(x-12*s,y-15*s,x-12*s+24*s*clamp(u.hp/HP[u.type],0,1),y-12*s,p);
                if(u.aircraft()) {fill(0xCC111A20);canvas.drawRoundRect(x-14*s,y+12*s,x+14*s,y+16*s,2*s,2*s,p);fill(gold);canvas.drawRect(x-14*s,y+12*s,x-14*s+28*s*clamp(u.fuel/100f,0,1),y+16*s,p);}
            }
        }
        boolean visibleToPlayer(float x,float y){
            for(Unit u:units)if(u.team==BLUE&&dist(x,y,u.x,u.y)<(u.type==FIGHTER||u.type==AIR?240:150))return true;
            for(Building b:buildings)if(b.team==BLUE&&b.type==5&&dist(x,y,b.x,b.y)<370)return true;
            return dist(x,y,bases.get(0).x,bases.get(0).y)<190;
        }
        void drawFog(){
            // Fog is drawn as darkened patches where player scouts have no vision.
            float s=Math.min(sx,sy);
            for(int gx=0;gx<1500;gx+=45)for(int gy=90;gy<850;gy+=45){
                if(!visibleToPlayer(gx,gy)){fill(0xA50A141B);canvas.drawRect(gx*sx,gy*sy,(gx+45)*sx,(gy+45)*sy,p);}
            }
        }
        void drawHUD(){
            float s=Math.min(sx,sy);fill(0xEA101D25);canvas.drawRect(0,0,W,85*s,p);
            txt("WAR OF DOTS: FRONTLINE",18*s,26*s,20*s,Color.WHITE,true);
            txt("CREDITS "+credits,18*s,55*s,14*s,gold,true);
            txt("HQ "+(int)bases.get(0).hp,145*s,55*s,13*s,blue,true);
            txt("ENEMY HQ "+(int)bases.get(1).hp,235*s,55*s,13*s,red,true);
            txt("CAPTURED "+captured(BLUE)+"/3",370*s,55*s,13*s,mint,true);
            txt(ai==0?"SMART AI":"DIVINELY SMART AI",Math.max(600*s,W-385*s),25*s,15*s,ai==0?mint:gold,true);
            txt(gameMode==0?"SKIRMISH":"MISSION "+(mission+1),Math.max(600*s,W-385*s),51*s,12*s,Color.LTGRAY,false);
            float y=H-64*s,bw=92*s,bh=45*s,g=5*s,x=12*s;
            for(int i=0;i<6;i++){int col=i==selectedType?0xFF287AA4:0xFF354B55;production[i]=new RectF(x+i*(bw+g),y,x+i*(bw+g)+bw,y+bh);button(production[i],UNIT_NAME[i],"$"+COST[i],col);}
            float rx=W-345*s;
            aiButton=new RectF(rx,y,rx+110*s,y+bh);button(aiButton,ai==0?"AI: SMART":"AI: DIVINE","TOGGLE",ai==0?0xFF354B55:0xFF71521C);
            pauseButton=new RectF(rx+116*s,y,rx+190*s,y+bh);button(pauseButton,paused?"RESUME":"PAUSE","",0xFF354B55);
            commandButton=new RectF(rx+196*s,y,rx+270*s,y+bh);button(commandButton,commandName(),"MODE",0xFF354B55);
            restartButton=new RectF(W-69*s,10*s,W-10*s,40*s);button(restartButton,"NEW","",0xFF783642);
            txt("Tap unit / drag squad • tap destination • command mode: "+commandName(),12*s,H-74*s,10*s,Color.LTGRAY,false);
            float bx=12*s,by=95*s;
            for(int i=0;i<6;i++){buildButtons[i]=new RectF(bx+i*72*s,by,bx+(i+1)*72*s-3*s,by+32*s);button(buildButtons[i],new String[]{"BARRACK","FACTORY","AIRFIELD","TURRET","POWER","RADAR"}[i],"$"+new int[]{140,190,210,160,130,175}[i],0xFF3B4D47);}
        }
        String commandName(){return new String[]{"MOVE","ATTACK","DEFEND","RETREAT"}[commandMode];}
        void button(RectF r,String a,String b,int col){float s=Math.min(sx,sy);fill(col);canvas.drawRoundRect(r,7*s,7*s,p);txt(a,r.left+5*s,r.top+17*s,10*s,Color.WHITE,true);if(!b.isEmpty())txt(b,r.left+5*s,r.top+34*s,9*s,gold,true);}
        void drawMenu(){
            float s=Math.min(sx,sy);fill(0xEE0C1720);canvas.drawRect(0,0,W,H,p);
            txt("WAR OF DOTS",W*.5f-135*s,H*.24f,40*s,Color.WHITE,true);
            txt("FRONTLINE",W*.5f-85*s,H*.31f,26*s,mint,true);
            txt("A deeper mobile RTS • Ground • Air • Bases • Objectives",W*.5f-220*s,H*.39f,15*s,Color.LTGRAY,false);
            menuMode=new RectF(W*.5f-180*s,H*.46f,W*.5f+180*s,H*.46f+55*s);
            button(menuMode,gameMode==0?"MODE: SKIRMISH":"MODE: CAMPAIGN","TAP TO CHANGE",0xFF354B55);
            menuAi=new RectF(W*.5f-180*s,H*.46f+66*s,W*.5f+180*s,H*.46f+121*s);
            button(menuAi,ai==0?"AI: SMART":"AI: DIVINELY SMART","TAP TO CHANGE",ai==0?0xFF354B55:0xFF71521C);
            menuMission=new RectF(W*.5f-180*s,H*.46f+128*s,W*.5f+180*s,H*.46f+174*s);
            button(menuMission,"MISSION "+(mission+1),"TAP TO CHANGE",0xFF354B55);
            menuStart=new RectF(W*.5f-180*s,H*.46f+181*s,W*.5f+180*s,H*.46f+241*s);
            button(menuStart,"START BATTLE","",0xFF176B63);
            txt("Tactics • Production • Air power • No technology tree",W*.5f-205*s,H*.46f+264*s,12*s,Color.LTGRAY,false);
        }
        void overlay(String title,String sub){fill(0xCC071016);canvas.drawRect(0,0,W,H,p);float s=Math.min(sx,sy);txt(title,W*.5f-title.length()*13*s,H*.46f,40*s,title.equals("VICTORY")?mint:Color.WHITE,true);txt(sub,W*.5f-140*s,H*.54f,15*s,Color.LTGRAY,false);}
        int captured(int team){int n=0;for(Point pt:points)if(pt.owner==team)n++;return n;}
        void update(float dt){
            matchTime+=dt;spawn+=dt;income+=dt;aiClock+=dt;
            if(income>=2){credits+=2+captured(BLUE)*3;enemyCredits+=2+captured(RED)*3;income=0;}
            if(spawn>=4.2f){
                int type=enemyChoose();if(enemyCredits>=COST[type]){enemyCredits-=COST[type];units.add(new Unit(1325,380+random.nextInt(130),RED,type));}
                spawn=0;
            }
            if(aiClock>(ai==0?2.4f:.8f)){aiPlan();aiClock=0;}
            for(Point pt:points){
                int b=0,r=0;for(Unit u:units)if(dist(u.x,u.y,pt.x,pt.y)<75){if(u.team==BLUE)b++;else r++;}
                if(b>r&&b>0){pt.progress=clamp(pt.progress+dt*(.12f+b*.035f),-1,1);if(pt.progress>=1)pt.owner=BLUE;}
                else if(r>b&&r>0){pt.progress=clamp(pt.progress-dt*(.12f+r*.035f),-1,1);if(pt.progress<=-1)pt.owner=RED;}
                else if(b==0&&r==0)pt.progress*=Math.max(0,1-dt*.04f);
            }
            ArrayList<Unit> dead=new ArrayList<>();
            for(Unit u:units){
                Unit enemy=nearestEnemy(u);Base enemyBase=bases.get(1-u.team);
                float tx,ty;
                if(u.team==BLUE&&u.selected&&u.hasOrder){tx=u.tx;ty=u.ty;}
                else if(u.team==RED&&ai==1){Point goal=bestPointForAI();if(goal!=null){tx=goal.x;ty=goal.y;}else{tx=enemyBase.x;ty=enemyBase.y;}}
                else {tx=enemyBase.x;ty=enemyBase.y;}
                if(u.type==TRANSPORT && !u.deployed && dist(u.x,u.y,tx,ty)<45){
                    for(int drop=0;drop<3;drop++){
                        units.add(new Unit(clamp(u.x+random.nextInt(45)-22,25,1475),
                            clamp(u.y+random.nextInt(45)-22,95,830),u.team,INFANTRY));
                    }
                    u.deployed=true;
                    u.hp-=1; // Transport remains as a light support aircraft after unloading.
                }
                if(u.aircraft()){
                    u.fuel-=dt*(u.type==BOMBER?1.25f:.8f);
                    if(u.fuel<18){tx=bases.get(u.team).x;ty=bases.get(u.team).y;u.retreating=true;}
                    if(u.retreating&&dist(u.x,u.y,bases.get(u.team).x,bases.get(u.team).y)<55){u.fuel=100;u.retreating=false;}
                    if(u.fuel<=0){u.hp-=dt*12;if(u.hp<=0){dead.add(u);continue;}}
                }
                float d=dist(u.x,u.y,tx,ty);
                if(d>RANGE[u.type]*.78f&&d>2){u.x+=(tx-u.x)/d*SPEED[u.type]*dt;u.y+=(ty-u.y)/d*SPEED[u.type]*dt;}
                u.x=clamp(u.x,25,1475);u.y=clamp(u.y,95,830);u.cd-=dt;
                if(u.cd<=0){
                    Unit target=null;float bd=Float.MAX_VALUE;
                    for(Unit v:units)if(v.team!=u.team&&visibleToTeam(u.team,v.x,v.y)){
                        float dd=dist(u.x,u.y,v.x,v.y);if(dd<=RANGE[u.type]&&dd<bd){bd=dd;target=v;}
                    }
                    if(target!=null){float damage=DAMAGE[u.type];if(u.type==TANK&&target.aircraft())damage*=.45f;if(u.type==FIGHTER&&target.aircraft())damage*=1.4f;if(u.type==BOMBER&&target.type==TANK)damage*=1.5f;target.hp-=damage;u.cd=COOLDOWN[u.type];}
                    else if(dist(u.x,u.y,enemyBase.x,enemyBase.y)<RANGE[u.type]+36){enemyBase.hp-=DAMAGE[u.type]*(u.type==BOMBER?.8f:.35f);u.cd=COOLDOWN[u.type];}
                }
                if(u.hp<=0)dead.add(u);
            }
            units.removeAll(dead);
            // Defensive turrets attack automatically.
            for(Building b:buildings)if(b.type==3){
                Unit target=null;float bd=180;
                for(Unit u:units)if(u.team!=b.team){float d=dist(b.x,b.y,u.x,u.y);if(d<bd){bd=d;target=u;}}
                if(target!=null){target.hp-=dt*11;}
            }
            // Production structures add a small passive income; power buildings improve it.
            if(((int)matchTime)%12==0&&((int)(matchTime-dt))%12!=0){
                for(Building b:buildings)if(b.team==BLUE&&b.type==4)credits+=35;
                for(Building b:buildings)if(b.team==RED&&b.type==4)enemyCredits+=35;
            }
            if(bases.get(0).hp<=0||bases.get(1).hp<=0){ended=true;victory=bases.get(1).hp<=0;message=victory?"Enemy HQ destroyed!":"Your HQ was destroyed.";}
            if(gameMode==1&&!ended&&mission==0&&captured(BLUE)>=2){ended=true;victory=true;message="Mission complete: secure two control points.";}
            if(gameMode==1&&!ended&&mission==1&&matchTime>150&&bases.get(0).hp>0){ended=true;victory=true;message="Mission complete: survive for 2:30.";}
        }
        boolean visibleToTeam(int team,float x,float y){
            if(team==BLUE)return visibleToPlayer(x,y);
            for(Unit u:units)if(u.team==RED&&dist(x,y,u.x,u.y)<(u.aircraft()?220:150))return true;
            return dist(x,y,bases.get(1).x,bases.get(1).y)<190;
        }
        Point bestPointForAI(){
            Point best=null;float score=Float.MAX_VALUE;
            for(Point pt:points){float d=dist(1350,450,pt.x,pt.y)+(pt.owner==RED?400:0);if(d<score){score=d;best=pt;}}
            return best;
        }
        int enemyChoose(){
            if(ai==1){
                int air=0,tank=0;for(Unit u:units)if(u.team==BLUE){if(u.aircraft())air++;if(u.type==TANK)tank++;}
                if(air>tank&&enemyCredits>=COST[FIGHTER])return FIGHTER;
                if(tank>air&&enemyCredits>=COST[BOMBER])return BOMBER;
                if(enemyCredits>=COST[TANK]&&random.nextFloat()<.5f)return TANK;
                if(enemyCredits>=COST[FIGHTER]&&random.nextFloat()<.35f)return FIGHTER;
            }
            return enemyCredits>=COST[TANK]&&random.nextFloat()<.3f?TANK:INFANTRY;
        }
        void aiPlan(){
            for(Unit u:units)if(u.team==RED){
                Unit target=nearestEnemy(u);
                if(ai==1&&target!=null&&target.hp<HP[target.type]*.5f){u.tx=target.x;u.ty=target.y;u.hasOrder=true;}
                else {Point pt=bestPointForAI();if(pt!=null){u.tx=pt.x;u.ty=pt.y;u.hasOrder=true;}}
            }
            message=ai==0?"Smart AI: expanding and attacking.":"Divinely Smart AI: counter-building, scouting and contesting objectives.";
        }
        Unit nearestEnemy(Unit u){Unit best=null;float bd=Float.MAX_VALUE;for(Unit v:units)if(v.team!=u.team){float d=dist(u.x,u.y,v.x,v.y);if(ai==1&&u.team==RED&&v.hp<HP[v.type]*.5f)d*=.75f;if(d<bd){bd=d;best=v;}}return best;}
        boolean hasProductionBuilding(int team,int unitType){
            int required = unitType==INFANTRY ? 0 : unitType==TANK ? 1 : 2;
            for(Building b:buildings) if(b.team==team && b.type==required) return true;
            return false;
        }
        void deployBuilding(int type,float wx,float wy){
            int[] costs={140,190,210,160,130,175};
            if(credits<costs[type]){message="Not enough credits to build "+buildingNames[type]+".";return;}
            if(dist(wx,wy,bases.get(0).x,bases.get(0).y)>420){message="Build closer to your HQ.";return;}
            for(Building b:buildings)if(dist(wx,wy,b.x,b.y)<55){message="That building spot is occupied.";return;}
            credits-=costs[type];buildings.add(new Building(wx,wy,BLUE,type));message=buildingNames[type]+" constructed.";
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX(),y=e.getY(),s=Math.min(sx,sy);
            if(menu){
                if(e.getAction()==MotionEvent.ACTION_UP){
                    if(menuMode!=null&&menuMode.contains(x,y)){gameMode=1-gameMode;return true;}
                    if(menuAi!=null&&menuAi.contains(x,y)){ai=1-ai;return true;}
                    if(menuMission!=null&&menuMission.contains(x,y)){mission=(mission+1)%3;return true;}
                    if(menuStart!=null&&menuStart.contains(x,y)){restart();return true;}
                }return true;
            }
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                dragX=dragNowX=x;dragY=dragNowY=y;dragging=y>85*s&&y<H-70*s;return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){dragNowX=x;dragNowY=y;return true;}
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            boolean wasDrag=dragging&&dist(dragX,dragY,x,y)>18*s;dragging=false;
            if(restartButton!=null&&restartButton.contains(x,y)){menu=true;return true;}
            if(aiButton!=null&&aiButton.contains(x,y)){ai=1-ai;message=ai==0?"Smart AI selected.":"Divinely Smart AI selected.";return true;}
            if(pauseButton!=null&&pauseButton.contains(x,y)){paused=!paused;return true;}
            if(commandButton!=null&&commandButton.contains(x,y)){commandMode=(commandMode+1)%4;message="Command mode: "+commandName();return true;}
            for(int i=0;i<6;i++)if(production[i]!=null&&production[i].contains(x,y)){
                selectedType=i;
                if(!hasProductionBuilding(BLUE,i)){message=i==INFANTRY?"Build a barracks first.":i==TANK?"Build a factory first.":"Build an airfield first.";}
                else if(credits>=COST[i]){credits-=COST[i];units.add(new Unit(150+random.nextInt(45),400+random.nextInt(90),BLUE,i));message=UNIT_NAME[i]+" deployed.";}
                else message="Not enough credits for "+UNIT_NAME[i]+".";
                return true;
            }
            for(int i=0;i<6;i++)if(buildButtons[i]!=null&&buildButtons[i].contains(x,y)){
                pendingBuilding=i;message="Tap a clear spot near your HQ to place "+buildingNames[i]+".";return true;
            }
            if(ended)return true;
            float wx=x/sx,wy=y/sy;
            if(pendingBuilding>=0){deployBuilding(pendingBuilding,wx,wy);pendingBuilding=-1;return true;}
            if(wasDrag){
                if(selected!=null)selected.selected=false;selected=null;
                float left=Math.min(dragX,x)/sx,right=Math.max(dragX,x)/sx,top=Math.min(dragY,y)/sy,bottom=Math.max(dragY,y)/sy;
                for(Unit u:units)if(u.team==BLUE&&u.x>=left&&u.x<=right&&u.y>=top&&u.y<=bottom){u.selected=true;if(selected==null)selected=u;}
                message=selected==null?"No friendly units in selection box.":"Squad selected: tap a destination.";
                return true;
            }
            Unit tapped=null;float bd=Float.MAX_VALUE;
            for(Unit u:units)if(u.team==BLUE){float d=dist(wx,wy,u.x,u.y);if(d<26&&d<bd){bd=d;tapped=u;}}
            if(tapped!=null){
                if(!tapped.selected){if(selected!=null)selected.selected=false;for(Unit u:units)u.selected=false;tapped.selected=true;selected=tapped;}
                else {tapped.selected=false;selected=null;}
                message="Unit selection updated. Tap destination to issue "+commandName().toLowerCase()+" order.";return true;
            }
            if(y>85*s&&y<H-70*s){
                boolean any=false;for(Unit u:units)if(u.team==BLUE&&u.selected){any=true;float ox=u.x,oy=u.y;
                    if(commandMode==3){u.tx=bases.get(0).x;u.ty=bases.get(0).y;u.retreating=true;}
                    else if(commandMode==2){u.tx=ox;u.ty=oy;u.hasOrder=false;}
                    else {float offset=(random.nextFloat()-.5f)*45;u.tx=wx+offset;u.ty=wy+offset;u.hasOrder=true;u.retreating=false;}
                }
                if(any){message=commandMode==1?"Attack order issued.":commandMode==2?"Defend position order issued.":commandMode==3?"Retreat to HQ order issued.":"Move order issued.";}
                else message="Select one or more friendly units first.";
            }
            return true;
        }
        int pendingBuilding=-1;
    }
}
