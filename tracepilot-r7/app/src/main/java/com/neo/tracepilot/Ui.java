package com.neo.tracepilot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
  static final int BG=Color.rgb(5,8,13), SURFACE=Color.rgb(12,18,27), SURFACE2=Color.rgb(17,25,37);
  static final int BORDER=Color.rgb(34,48,67), TEXT=Color.rgb(242,247,251), MUTED=Color.rgb(142,158,176);
  static final int CYAN=Color.rgb(78,215,255), PURPLE=Color.rgb(125,103,255), GREEN=Color.rgb(87,226,148);
  static final int YELLOW=Color.rgb(255,200,83), RED=Color.rgb(255,104,118), BLUE=Color.rgb(91,155,255), ORANGE=Color.rgb(255,151,84);
  static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
  static GradientDrawable bg(Context c,int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
  static GradientDrawable stroke(Context c,int color,int stroke,float radius){GradientDrawable g=bg(c,color,radius);g.setStroke(dp(c,1),stroke);return g;}
  static TextView text(Context c,String s,float sp,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
  static TextView title(Context c,String s){return text(c,s,25,TEXT,true);}
  static TextView section(Context c,String s){TextView t=text(c,s.toUpperCase(),11,CYAN,true);t.setLetterSpacing(.08f);return t;}
  static TextView muted(Context c,String s){return text(c,s,13,MUTED,false);}
  static LinearLayout card(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(c,16),dp(c,15),dp(c,16),dp(c,15));l.setBackground(stroke(c,SURFACE,BORDER,18));return l;}
  static Button button(Context c,String s,boolean primary){Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(primary?Color.rgb(2,18,28):TEXT);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,48));b.setPadding(dp(c,14),0,dp(c,14),0);b.setBackground(primary?bg(c,CYAN,14):stroke(c,SURFACE2,BORDER,14));return b;}
  static Button chip(Context c,String s,boolean selected){Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(selected?BG:MUTED);b.setMinHeight(dp(c,38));b.setPadding(dp(c,10),0,dp(c,10),0);b.setBackground(selected?bg(c,CYAN,18):stroke(c,SURFACE,BORDER,18));return b;}
  static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  static LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
  static LinearLayout.LayoutParams weight(float w){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,w);}
  static View gap(Context c,int d){View v=new View(c);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,d)));return v;}
  static int modeColor(String m){if("walk".equals(m))return GREEN;if("run".equals(m))return ORANGE;if("bike".equals(m))return YELLOW;if("car".equals(m))return BLUE;if("train".equals(m))return PURPLE;if("boat".equals(m))return CYAN;if("air".equals(m))return RED;return Color.rgb(170,184,198);}
}
